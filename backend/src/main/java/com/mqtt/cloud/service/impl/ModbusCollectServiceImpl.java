package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.collect.CollectRequest;
import com.mqtt.cloud.collect.PointCollector;
import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.common.constant.ProtocolTypeValue;
import com.mqtt.cloud.config.ModbusCollectExecutorConfig;
import com.mqtt.cloud.config.ModbusCollectProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.mapper.DevicePointTableMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ModbusCollectService;
import com.mqtt.cloud.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 平台侧 Modbus 采集调度实现（T-31 P4）。
 * <p>
 * 只负责「到期点位 → 设备任务 → 驱动采集 → 日志 / 摄取」的编排，协议细节全部在
 * {@link PointCollector} 实现内。采集结果不直接写属性表，而是组装成与 MQTT 上报同形的
 * Alink {@code data} 报文投入 {@link IngestPipeline}，从而自动获得物模型解析、影子、规则、
 * 告警、场景与属性历史五条旁路。
 */
@Slf4j
@Service
public class ModbusCollectServiceImpl implements ModbusCollectService {

    private static final String DEFAULT_DRIVER_ID = "modbus-tcp";
    private static final String PRODUCT_DISABLED = "DISABLED";
    private static final String METHOD_PROPERTY_POST = "thing.event.property.post";
    private static final String MESSAGE_TYPE_DATA = "data";

    private final DevicePointTableMapper pointTableMapper;
    private final DeviceService deviceService;
    private final ProductService productService;
    private final Map<String, PointCollector> collectors;
    private final PointRunRecorder runRecorder;
    private final IngestPipeline ingestPipeline;
    private final IngestProperties ingestProperties;
    private final ObjectMapper objectMapper;
    private final ModbusCollectProperties properties;
    private final Executor collectExecutor;

    /**
     * 在途设备：任务提交后、采集日志落库前，下一轮巡检仍会把同批点位判为到期。
     * 不去重会重复打开连接、重复读寄存器，并在日志里留下成对的重复行。
     */
    private final Set<Long> inFlightDevices = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ModbusCollectServiceImpl(DevicePointTableMapper pointTableMapper,
                                    DeviceService deviceService,
                                    ProductService productService,
                                    List<PointCollector> collectors,
                                    PointRunRecorder runRecorder,
                                    IngestPipeline ingestPipeline,
                                    IngestProperties ingestProperties,
                                    ObjectMapper objectMapper,
                                    ModbusCollectProperties properties,
                                    @Qualifier(ModbusCollectExecutorConfig.EXECUTOR_NAME) Executor collectExecutor) {
        this.pointTableMapper = pointTableMapper;
        this.deviceService = deviceService;
        this.productService = productService;
        this.collectors = collectors == null ? Map.of() : collectors.stream()
                .collect(Collectors.toMap(
                        collector -> collector.driverId().trim().toLowerCase(Locale.ROOT),
                        collector -> collector,
                        (first, second) -> first));
        this.runRecorder = runRecorder;
        this.ingestPipeline = ingestPipeline;
        this.ingestProperties = ingestProperties;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.collectExecutor = collectExecutor;
    }

    @Override
    public int sweepDuePoints() {
        int limit = Math.max(1, properties.getBatchLimit());
        int minInterval = Math.max(1, properties.getMinIntervalMs());
        List<DevicePointTable> due = pointTableMapper.findDuePlatformPoints(limit, minInterval);
        if (due == null || due.isEmpty()) {
            return 0;
        }
        Map<Long, List<DevicePointTable>> byDevice = new LinkedHashMap<>();
        for (DevicePointTable point : due) {
            byDevice.computeIfAbsent(point.getDeviceId(), key -> new ArrayList<>()).add(point);
        }

        int maxDevices = Math.max(1, properties.getEnabledDeviceLimit());
        int scheduled = 0;
        for (Map.Entry<Long, List<DevicePointTable>> entry : byDevice.entrySet()) {
            if (scheduled >= maxDevices) {
                break;
            }
            Long deviceId = entry.getKey();
            if (!inFlightDevices.add(deviceId)) {
                continue;
            }
            List<DevicePointTable> points = List.copyOf(entry.getValue());
            try {
                collectExecutor.execute(() -> {
                    try {
                        collectDeviceSafe(deviceId, points);
                    } finally {
                        inFlightDevices.remove(deviceId);
                    }
                });
                scheduled++;
            } catch (RuntimeException e) {
                inFlightDevices.remove(deviceId);
                log.warn("Modbus 采集任务提交失败: deviceId={}", deviceId, e);
            }
        }
        return scheduled;
    }

    /** 线程池入口：任何异常都必须在本方法内收敛，不能冒泡到线程池。 */
    void collectDeviceSafe(Long deviceId, List<DevicePointTable> points) {
        try {
            collectDevice(deviceId, points);
        } catch (Exception e) {
            log.warn("Modbus 采集任务异常: deviceId={}, points={}, error={}",
                    deviceId, points.size(), e.getMessage(), e);
            try {
                runRecorder.failed(points, "采集任务异常: " + message(e));
            } catch (Exception recordError) {
                log.warn("Modbus 采集失败日志落库也失败: deviceId={}", deviceId, recordError);
            }
        }
    }

    void collectDevice(Long deviceId, List<DevicePointTable> points) {
        Device device = deviceService.getById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            runRecorder.skipped(points, "设备不存在或已删除");
            return;
        }
        if (!Integer.valueOf(1).equals(device.getEnabled())) {
            runRecorder.skipped(points, "设备连接许可已禁用");
            return;
        }
        Product product;
        try {
            product = productService.requireById(device.getProductId());
        } catch (Exception e) {
            runRecorder.failed(points, "产品不存在或不可用: " + message(e));
            return;
        }
        if (PRODUCT_DISABLED.equals(product.getStatus())) {
            runRecorder.skipped(points, "产品已停用");
            return;
        }
        if (!ProtocolTypeValue.MODBUS.equals(ProtocolTypeValue.normalize(product.getProtocolType()))) {
            runRecorder.skipped(points, "产品接入协议不是 MODBUS: " + product.getProtocolType());
            return;
        }

        String driverId = StringUtils.hasText(product.getDriverId())
                ? product.getDriverId().trim().toLowerCase(Locale.ROOT)
                : DEFAULT_DRIVER_ID;
        PointCollector collector = collectors.get(driverId);
        if (collector == null) {
            runRecorder.failed(points, "未找到 Modbus 驱动: " + driverId);
            return;
        }

        CollectRequest request = new CollectRequest(points, product.getDriverConfig(),
                Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())),
                Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())));
        List<PointValue> values = collector.collect(request);
        if (values == null) {
            runRecorder.failed(points, "驱动未返回采集结果");
            return;
        }
        runRecorder.record(values);

        Set<Long> returned = values.stream().map(PointValue::pointId).collect(Collectors.toSet());
        List<DevicePointTable> missing = points.stream()
                .filter(point -> !returned.contains(point.getId()))
                .toList();
        if (!missing.isEmpty()) {
            runRecorder.failed(missing, "驱动未返回该点位结果");
        }

        List<PointValue> successes = values.stream().filter(PointValue::success).toList();
        if (successes.isEmpty()) {
            return;
        }
        publishToIngest(device, points, successes);
    }

    private void publishToIngest(Device device, List<DevicePointTable> points, List<PointValue> successes) {
        Map<Long, DevicePointTable> pointById = points.stream()
                .collect(Collectors.toMap(DevicePointTable::getId, point -> point, (first, second) -> first));
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "modbus-" + device.getId() + "-" + System.currentTimeMillis());
        root.put("version", "1.0");
        root.put("method", METHOD_PROPERTY_POST);
        ObjectNode params = root.putObject("params");
        for (PointValue value : successes) {
            DevicePointTable point = pointById.get(value.pointId());
            if (point == null || !StringUtils.hasText(value.pointKey()) || value.value() == null) {
                continue;
            }
            try {
                putTypedParam(params, value.pointKey(), point.getDataType(), value.value());
            } catch (Exception e) {
                log.warn("Modbus 点位值转 JSON 失败，跳过该点: deviceKey={}, pointKey={}, value={}",
                        device.getDeviceKey(), value.pointKey(), value.value());
            }
        }
        if (params.isEmpty()) {
            return;
        }
        if (!ingestProperties.isEnabled()) {
            log.warn("Modbus 采集成功但摄取管线已关闭，跳过入库: deviceKey={}, points={}",
                    device.getDeviceKey(), params.size());
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(root);
            String topic = "device/" + device.getDeviceKey() + "/data";
            String traceId = UUID.randomUUID().toString().replace("-", "");
            ingestPipeline.submit(new IngestRecord(device.getDeviceKey(), topic, MESSAGE_TYPE_DATA,
                    payload, 1, LocalDateTime.now(), traceId));
            log.debug("Modbus 采集已提交摄取: deviceKey={}, points={}", device.getDeviceKey(), params.size());
        } catch (Exception e) {
            log.warn("Modbus 采集结果组装 / 提交失败: deviceKey={}", device.getDeviceKey(), e);
        }
    }

    /**
     * 按点表 dataType 转成 JSON 原生类型：数值必须是 number、bool 必须是 boolean、
     * string 必须是 text —— 物模型解析会做类型校验，字符串数字会被判为非法。
     */
    private void putTypedParam(ObjectNode params, String pointKey, String dataType, String value) {
        String type = dataType == null ? "" : dataType.trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "bool" -> params.put(pointKey, "true".equalsIgnoreCase(value) || "1".equals(value));
            case "string" -> params.put(pointKey, value);
            default -> params.put(pointKey, new BigDecimal(value));
        }
    }

    private String message(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
