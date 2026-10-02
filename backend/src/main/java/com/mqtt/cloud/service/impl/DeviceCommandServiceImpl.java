package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.CommandProperties;
import com.mqtt.cloud.dto.response.ThingModelResponse;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceCommandRecordMapper;
import com.mqtt.cloud.mqtt.MqttClientManager;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 命令下发与服务调用实现（T-15 设计文档 §7 / §8）。
 * <p>
 * 前置顺序：准入校验 → 物模型存在 → 参数校验 → 落库 PENDING → 发布 → 置 SENT。
 * 下行统一 QoS 1、不保留；同步调用通过轮询本表等待终态（不用进程内 Future，多副本下回执可能落在其它副本）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandServiceImpl implements DeviceCommandService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SENT = "SENT";
    static final String STATUS_ACKED = "ACKED";
    static final String STATUS_FAILED = "FAILED";
    static final String STATUS_TIMEOUT = "TIMEOUT";

    /** 单页上限：防止 pageSize 被放大成全表扫描。 */
    static final long MAX_PAGE_SIZE = 100L;

    /** 单次巡检批量上限，避免一次扫全表。 */
    static final int SWEEP_LIMIT = 500;

    private static final String TOPIC_SUFFIX = "/cmd/down";
    private static final String METHOD_PROPERTY_SET = "thing.service.property.set";
    private static final String METHOD_PREFIX = "thing.service.";
    private static final String ALINK_VERSION = "1.0";
    private static final int PUBLISH_QOS = 1;
    private static final int DEVICE_ENABLED = 1;
    private static final int ERROR_MESSAGE_MAX = 255;

    private final DeviceService deviceService;
    private final ProductService productService;
    private final DeviceAccessGuard accessGuard;
    private final ThingModelService thingModelService;
    private final DeviceCommandRecordMapper commandMapper;
    private final MqttClientManager mqttClientManager;
    private final CommandProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public CommandCapability getCapability(Long productId) {
        ThingModelDefinition definition = loadDefinition(productId);
        if (definition.isEmpty()) {
            return new CommandCapability(0, List.of(), List.of());
        }
        List<ThingModelDefinition.PropertySpec> writable = definition.properties().values().stream()
                .filter(ThingModelDefinition.PropertySpec::writable)
                .toList();
        return new CommandCapability(definition.version(), writable,
                List.copyOf(definition.services().values()));
    }

    @Override
    public DeviceCommandRecord invoke(CommandInvoke command) {
        String commandType = normalizeType(command.commandType());
        String callType = normalizeCallType(command.callType());

        Device device = requireDevice(command.deviceId());
        requireInvocable(device);

        ThingModelDefinition definition = loadDefinition(device.getProductId());
        if (definition.isEmpty()) {
            throw new BusinessException(ResultCode.COMMAND_MODEL_MISSING);
        }
        requireWithinSizeLimit(command.paramsJson());
        JsonNode params = parseParams(command.paramsJson());
        validateParams(definition, commandType, command.identifier(), params);

        DeviceCommandRecord record = newPendingRecord(command, device, commandType, callType, params);
        commandMapper.insert(record);
        publish(record, device, commandType, params);

        return CALL_TYPE_SYNC.equals(callType) ? awaitTerminal(record) : record;
    }

    @Override
    public IPage<DeviceCommandRecord> listCommands(Long deviceId, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return commandMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<DeviceCommandRecord>lambdaQuery()
                        .eq(DeviceCommandRecord::getDeviceId, deviceId)
                        .orderByDesc(DeviceCommandRecord::getCreatedAt)
                        .orderByDesc(DeviceCommandRecord::getId));
    }

    /**
     * 超时巡检兜底：同步记录按 {@code sync-timeout-ms}、异步记录按 {@code async-timeout-ms}
     * 把长期停留 {@code PENDING/SENT} 的记录置 {@code TIMEOUT}（进程内等待者已消亡时仍有终态）。
     */
    @Override
    public int sweepTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        int swept = commandMapper.sweepTimeout(CALL_TYPE_SYNC,
                now.minusNanos(properties.getSyncTimeoutMs() * 1_000_000L), "同步命令超时（巡检兜底）", SWEEP_LIMIT);
        swept += commandMapper.sweepTimeout(CALL_TYPE_ASYNC,
                now.minusNanos(properties.getAsyncTimeoutMs() * 1_000_000L), "异步命令超时（巡检兜底）", SWEEP_LIMIT);
        return swept;
    }

    // ---------- 落库与发布 ----------

    private DeviceCommandRecord newPendingRecord(CommandInvoke command, Device device, String commandType,
                                                 String callType, JsonNode params) {
        DeviceCommandRecord record = new DeviceCommandRecord();
        record.setCommandId(UUID.randomUUID().toString());
        record.setDeviceId(device.getId());
        record.setProductId(device.getProductId());
        record.setCommandType(commandType);
        record.setIdentifier(TYPE_SERVICE.equals(commandType) ? command.identifier() : null);
        record.setParams(params.toString());
        record.setStatus(STATUS_PENDING);
        record.setCallType(callType);
        record.setSource(command.source() == null ? SOURCE_CONSOLE : command.source());
        record.setOperatorId(command.operatorId());
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private void publish(DeviceCommandRecord record, Device device, String commandType, JsonNode params) {
        String topic = "device/" + device.getDeviceKey() + TOPIC_SUFFIX;
        String payload = buildPayload(record.getCommandId(), commandType, record.getIdentifier(), params);
        LocalDateTime now = LocalDateTime.now();
        try {
            mqttClientManager.publish(topic, payload, PUBLISH_QOS);
        } catch (Exception e) {
            commandMapper.markFailed(record.getCommandId(), truncate("发布失败: " + e.getMessage()), now);
            log.warn("命令发布失败: commandId={}, topic={}", record.getCommandId(), topic, e);
            throw new BusinessException(ResultCode.MQTT_PUBLISH_FAILED);
        }
        commandMapper.markSent(record.getCommandId(), now);
        record.setStatus(STATUS_SENT);
        record.setSentAt(now);
    }

    /** Alink 下行载荷：{@code {id, version, method, params}}；{@code id} 即回执关联键。 */
    private String buildPayload(String commandId, String commandType, String identifier, JsonNode params) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", commandId);
        root.put("version", ALINK_VERSION);
        root.put("method", TYPE_PROPERTY_SET.equals(commandType) ? METHOD_PROPERTY_SET : METHOD_PREFIX + identifier);
        root.set("params", params);
        return root.toString();
    }

    /**
     * 同步等待：轮询命令记录至终态或超时。
     * <p>
     * 不用进程内 Future —— 多副本下回执经共享订阅只落到一个副本，请求所在副本可能等不到；
     * 记录在共享库中，轮询天然跨副本正确。
     */
    private DeviceCommandRecord awaitTerminal(DeviceCommandRecord record) {
        long interval = Math.max(1L, properties.getPollIntervalMs());
        long deadline = System.currentTimeMillis() + Math.max(0L, properties.getSyncTimeoutMs());
        while (System.currentTimeMillis() < deadline) {
            if (!sleep(interval)) {
                break;
            }
            DeviceCommandRecord latest = commandMapper.selectById(record.getId());
            if (latest != null && isTerminal(latest.getStatus())) {
                return latest;
            }
        }
        commandMapper.markTimeout(record.getCommandId(), "同步等待回执超时", LocalDateTime.now());
        DeviceCommandRecord latest = commandMapper.selectById(record.getId());
        return latest != null ? latest : record;
    }

    // ---------- 前置校验 ----------

    private Device requireDevice(Long deviceId) {
        Device device = deviceId == null ? null : deviceService.getById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    /** 准入判定复用 {@link DeviceAccessGuard}，与认证 / 授权回调同一入口，避免禁用语义分叉。 */
    private void requireInvocable(Device device) {
        if (device.getProductId() == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        Product product = productService.requireById(device.getProductId());
        DeviceAuthCacheService.AuthMeta meta = accessGuard.resolve(product.getProductKey(), device.getDeviceKey());
        if (accessGuard.isPermitted(meta)) {
            return;
        }
        throw new BusinessException(isDeviceEnabled(device)
                ? ResultCode.PRODUCT_DISABLED : ResultCode.DEVICE_DISABLED);
    }

    private boolean isDeviceEnabled(Device device) {
        return device.getEnabled() != null && device.getEnabled() == DEVICE_ENABLED;
    }

    private void validateParams(ThingModelDefinition definition, String commandType,
                                String identifier, JsonNode params) {
        if (TYPE_PROPERTY_SET.equals(commandType)) {
            if (identifier != null && !identifier.isBlank()) {
                throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "property_set 不得携带 identifier");
            }
            validatePropertySet(definition, params);
            return;
        }
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException(ResultCode.COMMAND_IDENTIFIER_UNKNOWN, "服务调用必须指定 identifier");
        }
        validateService(definition, identifier, params);
    }

    private void validatePropertySet(ThingModelDefinition definition, JsonNode params) {
        for (Map.Entry<String, JsonNode> entry : params.properties()) {
            String identifier = entry.getKey();
            ThingModelDefinition.PropertySpec spec = definition.properties().get(identifier);
            if (spec == null) {
                throw new BusinessException(ResultCode.COMMAND_IDENTIFIER_UNKNOWN, "属性未定义: " + identifier);
            }
            if (!spec.writable()) {
                throw new BusinessException(ResultCode.COMMAND_PROPERTY_READONLY, "属性不可写: " + identifier);
            }
            String error = ThingModelParamValidator.validate(spec, entry.getValue());
            if (error != null) {
                throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, identifier + " " + error);
            }
        }
    }

    private void validateService(ThingModelDefinition definition, String identifier, JsonNode params) {
        ThingModelDefinition.ServiceSpec service = definition.services().get(identifier);
        if (service == null) {
            throw new BusinessException(ResultCode.COMMAND_IDENTIFIER_UNKNOWN, "服务未定义: " + identifier);
        }
        for (Map.Entry<String, JsonNode> entry : params.properties()) {
            ThingModelDefinition.ParamSpec spec = service.input().get(entry.getKey());
            if (spec == null) {
                throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "未知入参: " + entry.getKey());
            }
            String error = ThingModelParamValidator.validate(spec, entry.getValue());
            if (error != null) {
                throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, entry.getKey() + " " + error);
            }
        }
        for (ThingModelDefinition.ParamSpec spec : service.input().values()) {
            if (spec.required() && !params.has(spec.identifier())) {
                throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "缺少必填入参: " + spec.identifier());
            }
        }
    }

    private void requireWithinSizeLimit(String paramsJson) {
        int max = properties.getMaxParamsBytes();
        if (max <= 0 || paramsJson == null) {
            return;
        }
        if (paramsJson.getBytes(StandardCharsets.UTF_8).length > max) {
            throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "params 超过大小上限 " + max + " 字节");
        }
    }

    private JsonNode parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "params 必须为非空 JSON 对象");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(paramsJson);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "params 不是合法 JSON");
        }
        if (node == null || !node.isObject() || node.isEmpty()) {
            throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "params 必须为非空 JSON 对象");
        }
        return node;
    }

    private String normalizeType(String type) {
        if (TYPE_PROPERTY_SET.equals(type) || TYPE_SERVICE.equals(type)) {
            return type;
        }
        throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "type 必须为 property_set 或 service");
    }

    private String normalizeCallType(String callType) {
        if (callType == null || callType.isBlank()) {
            return CALL_TYPE_ASYNC;
        }
        if (CALL_TYPE_SYNC.equals(callType) || CALL_TYPE_ASYNC.equals(callType)) {
            return callType;
        }
        throw new BusinessException(ResultCode.COMMAND_PARAM_INVALID, "callType 必须为 sync 或 async");
    }

    /** 命令路径不走上行解析缓存：{@code getForProduct} 受解析总开关约束，命令校验不应随其开关失效。 */
    private ThingModelDefinition loadDefinition(Long productId) {
        if (productId == null) {
            return ThingModelDefinition.EMPTY;
        }
        ThingModelResponse response = thingModelService.get(productId);
        if (response == null || response.getThingModel() == null) {
            return ThingModelDefinition.EMPTY;
        }
        int version = response.getVersion() == null ? 0 : response.getVersion();
        return ThingModelValidator.parseDefinition(response.getThingModel(), version);
    }

    private boolean isTerminal(String status) {
        return STATUS_ACKED.equals(status) || STATUS_FAILED.equals(status) || STATUS_TIMEOUT.equals(status);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX);
    }

    /** @return false 表示等待被中断，调用方应终止轮询 */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}