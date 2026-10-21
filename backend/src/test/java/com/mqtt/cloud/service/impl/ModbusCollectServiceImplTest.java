package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.collect.CollectRequest;
import com.mqtt.cloud.collect.PointCollector;
import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.common.constant.ProtocolTypeValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.ModbusCollectProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.mapper.DevicePointTableMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Modbus 采集调度单测（T-31 P4）。
 * <p>
 * 重点守「采集结果必须组装成 Alink 信封再进摄取管线」与「单设备异常不拖垮整轮」。
 */
class ModbusCollectServiceImplTest {

    private static final Long DEVICE_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private DevicePointTableMapper pointTableMapper;
    private DeviceService deviceService;
    private ProductService productService;
    private PointCollector collector;
    private PointRunRecorder recorder;
    private IngestPipeline ingestPipeline;
    private IngestProperties ingestProperties;
    private ModbusCollectProperties properties;
    private AtomicInteger executed;
    private ModbusCollectServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        pointTableMapper = mock(DevicePointTableMapper.class);
        deviceService = mock(DeviceService.class);
        productService = mock(ProductService.class);
        collector = mock(PointCollector.class);
        recorder = mock(PointRunRecorder.class);
        ingestPipeline = mock(IngestPipeline.class);
        ingestProperties = new IngestProperties();
        properties = new ModbusCollectProperties();
        executed = new AtomicInteger();
        Executor executor = command -> {
            executed.incrementAndGet();
            command.run();
        };
        when(collector.driverId()).thenReturn("modbus-tcp");
        service = new ModbusCollectServiceImpl(pointTableMapper, deviceService, productService,
                List.of(collector), recorder, ingestPipeline, ingestProperties, objectMapper,
                properties, executor);
    }

    @Test
    void sweep_should_collect_and_submit_alink_payload() throws Exception {
        List<DevicePointTable> points = List.of(
                point(101L, "temperature", "int", "0"),
                point(102L, "power", "bool", "1"),
                point(103L, "status", "string", "2"));
        when(pointTableMapper.findDuePlatformPoints(200, 1000)).thenReturn(points);
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(collector.collect(any())).thenReturn(List.of(
                value(101L, "temperature", "26", "26"),
                value(102L, "power", "true", "true"),
                value(103L, "status", "ok", "ok")));

        int scheduled = service.sweepDuePoints();

        assertThat(scheduled).isEqualTo(1);
        assertThat(executed.get()).isEqualTo(1);

        ArgumentCaptor<CollectRequest> requestCaptor = ArgumentCaptor.forClass(CollectRequest.class);
        verify(collector).collect(requestCaptor.capture());
        assertThat(requestCaptor.getValue().points()).hasSize(3);
        assertThat(requestCaptor.getValue().driverConfig()).contains("127.0.0.1");
        assertThat(requestCaptor.getValue().connectTimeout().toMillis()).isEqualTo(3000);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PointValue>> valuesCaptor = ArgumentCaptor.forClass(List.class);
        verify(recorder).record(valuesCaptor.capture());
        assertThat(valuesCaptor.getValue()).hasSize(3);

        ArgumentCaptor<IngestRecord> recordCaptor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(ingestPipeline).submit(recordCaptor.capture());
        IngestRecord record = recordCaptor.getValue();
        assertThat(record.deviceKey()).isEqualTo("dev-1");
        assertThat(record.topic()).isEqualTo("device/dev-1/data");
        assertThat(record.messageType()).isEqualTo("data");
        assertThat(record.qos()).isEqualTo(1);

        JsonNode payload = objectMapper.readTree(record.payload());
        assertThat(payload.get("method").asText()).isEqualTo("thing.event.property.post");
        JsonNode params = payload.get("params");
        assertThat(params.size()).isEqualTo(3);
        assertThat(params.get("temperature").isNumber()).isTrue();
        assertThat(params.get("temperature").asInt()).isEqualTo(26);
        assertThat(params.get("power").isBoolean()).isTrue();
        assertThat(params.get("power").asBoolean()).isTrue();
        assertThat(params.get("status").isTextual()).isTrue();
        assertThat(params.get("status").asText()).isEqualTo("ok");
    }

    @Test
    void no_due_points_should_return_zero_without_device_lookup() {
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt())).thenReturn(List.of());

        assertThat(service.sweepDuePoints()).isZero();
        assertThat(executed.get()).isZero();
        verify(deviceService, never()).getById(anyLong());
    }

    @Test
    void disabled_device_should_be_skipped_not_collected() {
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt()))
                .thenReturn(List.of(point(101L, "temperature", "int", "0")));
        Device device = device();
        device.setEnabled(0);
        when(deviceService.getById(DEVICE_ID)).thenReturn(device);

        service.sweepDuePoints();

        verify(recorder).skipped(any(), org.mockito.ArgumentMatchers.contains("设备连接许可已禁用"));
        verify(collector, never()).collect(any());
    }

    @Test
    void disabled_product_should_be_skipped() {
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt()))
                .thenReturn(List.of(point(101L, "temperature", "int", "0")));
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        Product product = product();
        product.setStatus("DISABLED");
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);

        service.sweepDuePoints();

        verify(recorder).skipped(any(), org.mockito.ArgumentMatchers.contains("产品已停用"));
        verify(collector, never()).collect(any());
    }

    @Test
    void non_modbus_product_should_be_skipped() {
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt()))
                .thenReturn(List.of(point(101L, "temperature", "int", "0")));
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        Product product = product();
        product.setProtocolType(ProtocolTypeValue.MQTT);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);

        service.sweepDuePoints();

        verify(recorder).skipped(any(), org.mockito.ArgumentMatchers.contains("不是 MODBUS"));
        verify(collector, never()).collect(any());
    }

    @Test
    void unknown_driver_should_fail_points() {
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt()))
                .thenReturn(List.of(point(101L, "temperature", "int", "0")));
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        Product product = product();
        product.setDriverId("opc-ua");
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);

        service.sweepDuePoints();

        verify(recorder).failed(any(), org.mockito.ArgumentMatchers.contains("未找到 Modbus 驱动"));
        verify(collector, never()).collect(any());
    }

    @Test
    void collector_failure_should_record_failed_but_not_submit() {
        List<DevicePointTable> points = List.of(point(101L, "temperature", "int", "0"));
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt())).thenReturn(points);
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(collector.collect(any())).thenReturn(List.of(
                PointValue.failed(101L, DEVICE_ID, "temperature", "read timeout")));

        service.sweepDuePoints();

        verify(recorder).record(any());
        verify(ingestPipeline, never()).submit(any());
    }

    @Test
    void collector_exception_should_be_contained_and_logged_as_failed() {
        List<DevicePointTable> points = List.of(point(101L, "temperature", "int", "0"));
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt())).thenReturn(points);
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(collector.collect(any())).thenThrow(new RuntimeException("boom"));

        service.sweepDuePoints();

        verify(recorder).failed(any(), org.mockito.ArgumentMatchers.contains("采集任务异常"));
        verify(ingestPipeline, never()).submit(any());
    }

    @Test
    void disabled_ingest_should_skip_submit_after_successful_collection() {
        List<DevicePointTable> points = List.of(point(101L, "temperature", "int", "0"));
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt())).thenReturn(points);
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(collector.collect(any())).thenReturn(List.of(value(101L, "temperature", "26", "26")));
        ingestProperties.setEnabled(false);

        service.sweepDuePoints();

        verify(recorder).record(any());
        verify(ingestPipeline, never()).submit(any());
    }

    @Test
    void missing_result_should_be_recorded_as_failed() {
        List<DevicePointTable> points = List.of(
                point(101L, "temperature", "int", "0"),
                point(102L, "power", "bool", "1"));
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt())).thenReturn(points);
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(collector.collect(any())).thenReturn(List.of(value(101L, "temperature", "26", "26")));

        service.sweepDuePoints();

        verify(recorder).failed(any(), org.mockito.ArgumentMatchers.contains("未返回该点位"));
    }

    @Test
    void device_limit_should_cap_submitted_tasks() {
        DevicePointTable p1 = point(101L, "temperature", "int", "0");
        DevicePointTable p2 = point(201L, "temperature", "int", "0");
        p2.setDeviceId(2L);
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt())).thenReturn(List.of(p1, p2));
        when(deviceService.getById(1L)).thenReturn(device());
        when(deviceService.getById(2L)).thenReturn(device(2L, "dev-2"));
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(collector.collect(any())).thenAnswer(invocation -> {
            CollectRequest request = invocation.getArgument(0);
            return request.points().stream()
                    .map(point -> value(point.getId(), point.getPointKey(), "1", "1"))
                    .toList();
        });
        properties.setEnabledDeviceLimit(1);

        assertThat(service.sweepDuePoints()).isEqualTo(1);
        assertThat(executed.get()).isEqualTo(1);
    }

    @Test
    void missing_product_should_fail_points() {
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt()))
                .thenReturn(List.of(point(101L, "temperature", "int", "0")));
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        when(productService.requireById(PRODUCT_ID))
                .thenThrow(new BusinessException(ResultCode.PRODUCT_NOT_FOUND));

        service.sweepDuePoints();

        verify(recorder).failed(any(), org.mockito.ArgumentMatchers.contains("产品不存在"));
    }

    @Test
    void missing_device_should_be_skipped() {
        when(pointTableMapper.findDuePlatformPoints(anyInt(), anyInt()))
                .thenReturn(List.of(point(101L, "temperature", "int", "0")));
        when(deviceService.getById(DEVICE_ID)).thenReturn(null);

        service.sweepDuePoints();

        verify(recorder).skipped(any(), org.mockito.ArgumentMatchers.contains("设备不存在"));
    }

    private Device device() {
        return device(DEVICE_ID, "dev-1");
    }

    private Device device(Long id, String key) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey(key);
        device.setProductId(PRODUCT_ID);
        device.setEnabled(1);
        device.setDeleted(0);
        return device;
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setStatus("ENABLED");
        product.setProtocolType(ProtocolTypeValue.MODBUS);
        product.setDriverConfig("{\"host\":\"127.0.0.1\"}");
        return product;
    }

    private DevicePointTable point(Long id, String key, String dataType, String address) {
        DevicePointTable point = new DevicePointTable();
        point.setId(id);
        point.setDeviceId(DEVICE_ID);
        point.setPointKey(key);
        point.setPointType(PointTypeValue.HOLDING_REGISTER);
        point.setAddress(address);
        point.setDataType(dataType);
        point.setExecutor("PLATFORM");
        point.setReadIntervalMs(1000);
        return point;
    }

    private PointValue value(Long pointId, String key, String raw, String value) {
        return new PointValue(pointId, DEVICE_ID, key, true, raw, value, null, LocalDateTime.now());
    }
}
