package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.CommandProperties;
import com.mqtt.cloud.config.ShadowProperties;
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
import com.mqtt.cloud.service.DeviceShadowService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** T-16：离线缓存待补发。 */
    static final String STATUS_QUEUED = "QUEUED";

    /** 设备在线判定（{@code status} 运行态，与 {@code enabled} 语义分离）。 */
    static final String STATUS_ONLINE = "ONLINE";

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
    private final ShadowProperties shadowProperties;
    private final DeviceShadowService deviceShadowService;
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

        if (TYPE_PROPERTY_SET.equals(commandType)) {
            // T-16 §9.1：期望值先落影子 desired（无论在线与否），delta 由服务端计算。
            applyDesired(definition, device.getId(), params);
            if (!isDeliverable(device)) {
                // 设备离线：不发布、不抛错，入队等待上线补发；同步调用也不等待（尚未下发）。
                enqueue(record, LocalDateTime.now());
                return record;
            }
        }

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
            if (TYPE_PROPERTY_SET.equals(commandType)) {
                // T-16 §9.1：属性设置的发布异常转为入队退避（不抛错），发布恢复后由补发链路接管。
                LocalDateTime nextAttemptAt = now.plusNanos(backoffMillis(1) * 1_000_000L);
                commandMapper.markQueued(record.getCommandId(), nextAttemptAt, truncate("发布失败: " + e.getMessage()));
                record.setStatus(STATUS_QUEUED);
                record.setNextAttemptAt(nextAttemptAt);
                log.warn("属性设置发布失败，转入离线补发队列: commandId={}, topic={}", record.getCommandId(), topic, e);
                return;
            }
            commandMapper.markFailed(record.getCommandId(), truncate("发布失败: " + e.getMessage()), now);
            log.warn("命令发布失败: commandId={}, topic={}", record.getCommandId(), topic, e);
            throw new BusinessException(ResultCode.MQTT_PUBLISH_FAILED);
        }
        commandMapper.markSent(record.getCommandId(), now);
        record.setStatus(STATUS_SENT);
        record.setSentAt(now);
    }

    // ---------- T-16 离线入队与补发 ----------

    /**
     * 入队：{@code PENDING → QUEUED}，{@code next_attempt_at = now}（设备上线后立即可补发）。
     * 期望值已在 {@link #applyDesired} 中落影子，此处只迁移命令状态。
     */
    private void enqueue(DeviceCommandRecord record, LocalDateTime now) {
        commandMapper.markQueued(record.getCommandId(), now, null);
        record.setStatus(STATUS_QUEUED);
        record.setNextAttemptAt(now);
    }

    /**
     * 可即时投递判定（T-16 §9.1）：{@code status='ONLINE' && enabled=1 && 产品 ENABLED}。
     * 产品 ENABLED 与设备 {@code enabled=1} 已由 {@link #requireInvocable} 保证，此处只剩在线判定。
     */
    private boolean isDeliverable(Device device) {
        return STATUS_ONLINE.equals(device.getStatus());
    }

    /** 属性设置期望值：按物模型归一化为 {@code identifier -> 文本} 后写入影子 {@code desired}。 */
    private void applyDesired(ThingModelDefinition definition, Long deviceId, JsonNode params) {
        Map<String, String> desired = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : params.properties()) {
            ThingModelDefinition.PropertySpec spec = definition.properties().get(entry.getKey());
            if (spec == null) {
                continue;
            }
            String normalized = ThingModelParamValidator.normalize(spec, entry.getValue());
            if (normalized != null) {
                desired.put(entry.getKey(), normalized);
            }
        }
        deviceShadowService.applyDesired(deviceId, desired);
    }

    /** {@inheritDoc} */
    @Override
    public int countQueued(Long deviceId) {
        return deviceId == null ? 0 : commandMapper.countQueuedByDevice(deviceId);
    }

    /**
     * 补发指定设备的 {@code QUEUED} 命令：按 {@code next_attempt_at} 升序取 {@code limit} 条，
     * 逐条发布。成功 {@code QUEUED → SENT}；失败按尝试次数决定「退避重试」或「次数耗尽置 FAILED」。
     * 逐条 try/catch 隔离，单条异常不影响其余命令。
     */
    @Override
    public int flushQueued(Long deviceId, int limit) {
        if (deviceId == null) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(limit, SWEEP_LIMIT));
        List<DeviceCommandRecord> queued = commandMapper.selectQueuedByDevice(deviceId, LocalDateTime.now(), safeLimit);
        if (queued.isEmpty()) {
            return 0;
        }
        Device device = deviceService.getById(deviceId);
        if (device == null) {
            log.warn("命令补发跳过：设备不存在, deviceId={}", deviceId);
            return 0;
        }
        int delivered = 0;
        for (DeviceCommandRecord record : queued) {
            try {
                if (resend(record, device)) {
                    delivered++;
                }
            } catch (Exception e) {
                log.warn("命令补发异常，跳过本条: commandId={}", record.getCommandId(), e);
            }
        }
        return delivered;
    }

    /**
     * 退避重试巡检：跨设备选取到期的 {@code QUEUED}（设备在线、启用且产品启用），
     * 按设备去重后逐个 {@link #flushQueued(Long, int)}。返回本轮成功补发条数。
     */
    @Override
    public int sweepQueuedRetries() {
        if (!shadowProperties.isResendEnabled()) {
            return 0;
        }
        int batch = Math.max(1, Math.min(shadowProperties.getResendBatchSize(), SWEEP_LIMIT));
        List<DeviceCommandRecord> due = commandMapper.selectDueQueued(LocalDateTime.now(), batch);
        if (due.isEmpty()) {
            return 0;
        }
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (DeviceCommandRecord record : due) {
            if (record.getDeviceId() != null) {
                deviceIds.add(record.getDeviceId());
            }
        }
        int delivered = 0;
        for (Long deviceId : deviceIds) {
            delivered += flushQueued(deviceId, batch);
        }
        return delivered;
    }

    /** 补发单条：发布成功置 SENT，返回 {@code true}；失败按退避或耗尽处理，返回 {@code false}。 */
    private boolean resend(DeviceCommandRecord record, Device device) {
        LocalDateTime now = LocalDateTime.now();
        JsonNode params;
        try {
            params = objectMapper.readTree(record.getParams());
        } catch (Exception e) {
            params = null;
        }
        if (params == null || !params.isObject()) {
            handleResendFailure(record, now, new IllegalStateException("params 非法"));
            return false;
        }
        String topic = "device/" + device.getDeviceKey() + TOPIC_SUFFIX;
        String payload = buildPayload(record.getCommandId(), record.getCommandType(), record.getIdentifier(), params);
        try {
            mqttClientManager.publish(topic, payload, PUBLISH_QOS);
        } catch (Exception e) {
            handleResendFailure(record, now, e);
            return false;
        }
        commandMapper.markSentFromQueued(record.getCommandId(), now);
        record.setStatus(STATUS_SENT);
        record.setSentAt(now);
        return true;
    }

    /** 补发失败处理：尝试次数 +1，达上限置 FAILED（{@code 补发重试次数耗尽}），否则按指数退避重设时间。 */
    private void handleResendFailure(DeviceCommandRecord record, LocalDateTime now, Exception cause) {
        int attempt = (record.getAttemptCount() == null ? 0 : record.getAttemptCount()) + 1;
        if (attempt >= Math.max(1, shadowProperties.getRetryMaxAttempts())) {
            commandMapper.markFailedFromQueued(record.getCommandId(), "补发重试次数耗尽", now);
            record.setStatus(STATUS_FAILED);
            log.warn("命令补发重试次数耗尽: commandId={}, attempt={}", record.getCommandId(), attempt);
            return;
        }
        long delay = backoffMillis(attempt);
        LocalDateTime nextAttemptAt = now.plusNanos(delay * 1_000_000L);
        commandMapper.markRetry(record.getCommandId(), nextAttemptAt, truncate("补发发布失败: " + cause.getMessage()));
        record.setAttemptCount(attempt);
        record.setNextAttemptAt(nextAttemptAt);
        log.warn("命令补发失败，{}ms 后重试: commandId={}, attempt={}", delay, record.getCommandId(), attempt, cause);
    }

    /** 指数退避：{@code min(base * 2^(attempt-1), maxDelay)}；{@code attempt} 从 1 起。 */
    private long backoffMillis(int attempt) {
        long base = Math.max(0L, shadowProperties.getRetryBaseDelayMs());
        long max = Math.max(base, shadowProperties.getRetryMaxDelayMs());
        long delay = Math.min(base, max);
        for (int i = 1; i < attempt && delay < max; i++) {
            delay = Math.min(delay * 2, max);
        }
        return delay;
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