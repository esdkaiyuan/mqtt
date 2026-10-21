package com.mqtt.cloud.service.impl;

import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import com.mqtt.cloud.collect.PointCollector;
import com.mqtt.cloud.collect.modbus.ModbusMasterRegistry;
import com.mqtt.cloud.collect.modbus.ModbusPointCollector;
import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.common.constant.ProtocolTypeValue;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Modbus 平台采集端到端用例（T-31 P6）。
 * <p>
 * 与 {@code ModbusLoopbackIntegrationTest} 的区别：本类走完整的服务编排——
 * 到期点位 → 专用线程池（同步执行）→ 真实 Modbus TCP 从站 → 三态日志 → Alink 报文 → 摄取入口。
 * 数据库与下游 fan-out 仍用 mock，因为仓库没有 DB 集成测试基建；真实现场从站仍需部署后实测。
 */
class ModbusCollectEndToEndTest {

    private ModbusSlave slave;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        if (slave != null) {
            ModbusSlaveFactory.close(slave);
            slave = null;
        }
    }

    @Test
    void real_slave_values_should_reach_ingest_pipeline_as_alink_data() throws Exception {
        int port = freePort();
        SimpleProcessImage image = new SimpleProcessImage(1);
        image.addRegister(new SimpleRegister(123));
        image.addRegister(new SimpleRegister(456));
        slave = ModbusSlaveFactory.createTCPSlave(port, 2);
        slave.addProcessImage(1, image);
        slave.open();

        DevicePointTableMapper mapper = mock(DevicePointTableMapper.class);
        DeviceService deviceService = mock(DeviceService.class);
        ProductService productService = mock(ProductService.class);
        PointRunRecorder recorder = mock(PointRunRecorder.class);
        IngestPipeline pipeline = mock(IngestPipeline.class);
        IngestProperties ingestProperties = new IngestProperties();
        ModbusCollectProperties properties = new ModbusCollectProperties();
        ModbusMasterRegistry registry = new ModbusMasterRegistry(properties);
        PointCollector collector = new ModbusPointCollector(registry, properties, objectMapper);

        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey("dev-1");
        device.setProductId(10L);
        device.setEnabled(1);
        device.setDeleted(0);
        Product product = new Product();
        product.setId(10L);
        product.setProductKey("env");
        product.setStatus("ENABLED");
        product.setProtocolType(ProtocolTypeValue.MODBUS);
        product.setDriverConfig("{\"host\":\"127.0.0.1\",\"port\":" + port + ",\"unitId\":1}");

        when(mapper.findDuePlatformPoints(anyInt(), anyInt())).thenReturn(List.of(
                point(101L, "temperature", "0"),
                point(102L, "humidity", "1")));
        when(deviceService.getById(1L)).thenReturn(device);
        when(productService.requireById(10L)).thenReturn(product);

        Executor directExecutor = Runnable::run;
        ModbusCollectServiceImpl service = new ModbusCollectServiceImpl(mapper, deviceService, productService,
                List.of(collector), recorder, pipeline, ingestProperties, objectMapper, properties, directExecutor);

        assertThat(service.sweepDuePoints()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.mqtt.cloud.collect.PointValue>> valuesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(recorder).record(valuesCaptor.capture());
        assertThat(valuesCaptor.getValue()).extracting(com.mqtt.cloud.collect.PointValue::value)
                .containsExactly("123", "456");

        ArgumentCaptor<IngestRecord> recordCaptor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(pipeline).submit(recordCaptor.capture());
        IngestRecord record = recordCaptor.getValue();
        assertThat(record.deviceKey()).isEqualTo("dev-1");
        assertThat(record.messageType()).isEqualTo("data");
        assertThat(record.topic()).isEqualTo("device/dev-1/data");

        JsonNode payload = objectMapper.readTree(record.payload());
        assertThat(payload.get("method").asText()).isEqualTo("thing.event.property.post");
        assertThat(payload.get("params").get("temperature").asInt()).isEqualTo(123);
        assertThat(payload.get("params").get("humidity").asInt()).isEqualTo(456);
        assertThat(registry.activeCount()).isZero();
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private DevicePointTable point(Long id, String key, String address) {
        DevicePointTable point = new DevicePointTable();
        point.setId(id);
        point.setDeviceId(1L);
        point.setPointKey(key);
        point.setPointType(PointTypeValue.HOLDING_REGISTER);
        point.setAddress(address);
        point.setDataType("int");
        point.setExecutor("PLATFORM");
        point.setReadIntervalMs(1000);
        return point;
    }
}
