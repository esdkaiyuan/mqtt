package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.RuleDefinition;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.RuleDefinitionMapper;
import com.mqtt.cloud.mapper.RuleExecutionMapper;
import com.mqtt.cloud.mqtt.RuleMqttForwarder;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.RuleActionExecutor;
import com.mqtt.cloud.service.RuleHttpForwarder;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.util.RuleTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则动作执行实现（T-19 设计文档 §8.5 / §8.6 / §8.7）。
 * <p>
 * 四类动作：{@code UPDATE_PROPERTY}（复用 {@code upsertIfNewer} 时间戳守卫）、
 * {@code SEND_COMMAND}（复用命令链路，强制 {@code async} + {@code source=RULE}）、
 * {@code FORWARD_MQTT}（独立出站 Paho 客户端）、{@code FORWARD_HTTP}（独立出站 POST + HMAC 签名）。
 * <p>
 * 失败一律转为 {@link ActionFailure}（携带可读原因）后统一走
 * {@link #scheduleRetryOrFail}：{@code attempt_count + 1 < retry-max-attempts} 时按
 * {@code min(base × 2^(n-1), max)} 退避重设 {@code next_attempt_at}，否则置 {@code FAILED}。
 * 非 {@link ActionFailure} 的意外异常只记 WARN，同样进入重试，绝不冒泡到线程池 / 巡检线程。
 * <p>
 * {@code reportedAt} 占位符取执行记录创建时间（触发时刻）——{@code rule_execution} 不单独持久化
 * 上报时间，避免为一处展示字段扩列。
 */
@Slf4j
@Service
public class RuleActionExecutorImpl implements RuleActionExecutor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int ERROR_MESSAGE_MAX = 512;
    private static final String HEADER_KEY = "headers";

    private final RuleExecutionMapper executionMapper;
    private final RuleDefinitionMapper definitionMapper;
    private final DeviceMapper deviceMapper;
    private final DevicePropertyLatestMapper propertyLatestMapper;
    private final DeviceCommandService commandService;
    private final ThingModelService thingModelService;
    private final RuleMqttForwarder mqttForwarder;
    private final RuleHttpForwarder httpForwarder;
    private final RuleProperties properties;
    private final ObjectMapper objectMapper;

    public RuleActionExecutorImpl(RuleExecutionMapper executionMapper,
                                  RuleDefinitionMapper definitionMapper,
                                  DeviceMapper deviceMapper,
                                  DevicePropertyLatestMapper propertyLatestMapper,
                                  DeviceCommandService commandService,
                                  ThingModelService thingModelService,
                                  RuleMqttForwarder mqttForwarder,
                                  RuleHttpForwarder httpForwarder,
                                  RuleProperties properties,
                                  ObjectMapper objectMapper) {
        this.executionMapper = executionMapper;
        this.definitionMapper = definitionMapper;
        this.deviceMapper = deviceMapper;
        this.propertyLatestMapper = propertyLatestMapper;
        this.commandService = commandService;
        this.thingModelService = thingModelService;
        this.mqttForwarder = mqttForwarder;
        this.httpForwarder = httpForwarder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(Long executionId) {
        RuleExecution execution = loadPending(executionId);
        if (execution == null) {
            return;
        }
        touchLastTriggered(execution.getRuleId());
        try {
            run(execution);
            markSuccess(execution);
        } catch (ActionFailure e) {
            scheduleRetryOrFail(execution, truncate(e.getMessage(), ERROR_MESSAGE_MAX));
        } catch (Exception e) {
            log.warn("规则动作执行异常: executionId={}", executionId, e);
            scheduleRetryOrFail(execution, truncate(describe(e), ERROR_MESSAGE_MAX));
        }
    }

    /** 读取待执行记录；不存在或已非 {@code PENDING}（已终态 / 已被其它副本处理）返回 {@code null}。 */
    private RuleExecution loadPending(Long executionId) {
        if (executionId == null) {
            return null;
        }
        try {
            RuleExecution execution = executionMapper.selectById(executionId);
            if (execution == null || !RuleConstants.STATUS_PENDING.equals(execution.getStatus())) {
                return null;
            }
            return execution;
        } catch (Exception e) {
            log.warn("规则执行记录读取失败: executionId={}", executionId, e);
            return null;
        }
    }

    /** 执行动作本体；失败抛 {@link ActionFailure}（携带原因）。 */
    private void run(RuleExecution execution) {
        RuleDefinition rule = definitionMapper.selectById(execution.getRuleId());
        if (rule == null) {
            throw new ActionFailure("规则已删除");
        }
        Device device = deviceMapper.selectById(execution.getDeviceId());
        if (device == null) {
            throw new ActionFailure("目标设备不存在或已删除");
        }
        if (execution.getUserId() == null || !execution.getUserId().equals(device.getOwnerId())) {
            throw new ActionFailure("目标设备归属已变更");
        }
        JsonNode config = readConfig(rule);
        if (config == null) {
            throw new ActionFailure("动作配置缺失或非法");
        }
        Map<String, String> values = buildPlaceholders(execution, rule, device);
        switch (rule.getActionType()) {
            case RuleConstants.ACTION_UPDATE_PROPERTY -> updateProperty(device, config, values);
            case RuleConstants.ACTION_SEND_COMMAND -> sendCommand(device, config, values);
            case RuleConstants.ACTION_FORWARD_MQTT -> forwardMqtt(execution, config, values);
            case RuleConstants.ACTION_FORWARD_HTTP -> forwardHttp(execution, rule, config, values);
            default -> throw new ActionFailure("不支持的动作类型 " + rule.getActionType());
        }
    }

    /** 更新云端属性：目标标识符必须在物模型中定义且可写，写入受时间戳守卫保护。 */
    private void updateProperty(Device device, JsonNode config, Map<String, String> values) {
        String target = text(config, "identifier");
        if (target == null) {
            throw new ActionFailure("更新属性动作缺少目标标识符");
        }
        ThingModelDefinition definition = thingModelService.getForProduct(device.getProductId());
        ThingModelDefinition.PropertySpec spec = definition.properties().get(target);
        if (spec == null) {
            throw new ActionFailure("目标属性 " + target + " 未在物模型中定义");
        }
        if (!spec.writable()) {
            throw new ActionFailure("目标属性 " + target + " 不可写（accessMode 非 rw）");
        }
        String renderedValue = RuleTemplateRenderer.render(text(config, "value"), values);
        if (renderedValue == null || renderedValue.isBlank()) {
            throw new ActionFailure("更新属性动作取值为空");
        }
        propertyLatestMapper.upsertIfNewer(device.getId(), target, spec.type(), renderedValue, LocalDateTime.now());
    }

    /**
     * 下发命令：复用命令链路（含物模型校验、影子写入、离线入队、超时巡检），强制 {@code async} + {@code source=RULE}。
     * <p>
     * {@code property_set} 的目标属性随 {@code params}（属性 → 取值对象）承载：命令链路对
     * {@code property_set} 禁止携带 {@code identifier}，配置中的 {@code identifier} 只用于保存期与试运行的物模型诊断。
     */
    private void sendCommand(Device device, JsonNode config, Map<String, String> values) {
        String commandType = text(config, "commandType");
        String identifier = text(config, "identifier");
        String paramsJson = renderJson(config.get("params"), values, "命令参数");
        boolean propertySet = DeviceCommandService.TYPE_PROPERTY_SET.equals(commandType);
        try {
            commandService.invoke(new DeviceCommandService.CommandInvoke(
                    device.getId(),
                    device.getProductId(),
                    commandType,
                    propertySet ? null : identifier,
                    paramsJson,
                    DeviceCommandService.CALL_TYPE_ASYNC,
                    DeviceCommandService.SOURCE_RULE,
                    null));
        } catch (Exception e) {
            throw new ActionFailure("下发命令失败：" + describe(e));
        }
    }

    /** 转发外部 MQTT：渲染 Topic / 载荷后出站到第三方 Broker，渲染成功即回填载荷快照。 */
    private void forwardMqtt(RuleExecution execution, JsonNode config, Map<String, String> values) {
        String topic = RuleTemplateRenderer.render(text(config, "topic"), values);
        if (topic == null || topic.isBlank()) {
            throw new ActionFailure("转发 MQTT 缺少 Topic");
        }
        int qos = intValue(config.get("qos"), properties.getMqtt().getQos());
        String payload = renderPayload(text(config, "payloadTemplate"), execution, values);
        updateForwardPayload(execution.getId(), payload);
        try {
            mqttForwarder.publish(topic, payload, qos);
        } catch (Exception e) {
            throw new ActionFailure("MQTT 转发失败：" + describe(e));
        }
    }

    /** 转发 HTTP：渲染 URL / 请求头 / 载荷后出站 POST/PUT，渲染成功即回填载荷快照。 */
    private void forwardHttp(RuleExecution execution, RuleDefinition rule, JsonNode config, Map<String, String> values) {
        String url = RuleTemplateRenderer.render(text(config, "url"), values);
        if (url == null || url.isBlank()) {
            throw new ActionFailure("转发 HTTP 缺少 URL");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ActionFailure("URL 必须以 http:// 或 https:// 开头");
        }
        String method = text(config, "method");
        Map<String, String> headers = renderHeaders(config.get(HEADER_KEY), values);
        String payload = renderPayload(text(config, "payloadTemplate"), execution, values);
        updateForwardPayload(execution.getId(), payload);
        try {
            httpForwarder.send(method, url, headers, payload, rule.getId(), execution.getId());
        } catch (Exception e) {
            throw new ActionFailure("HTTP 转发失败：" + describe(e));
        }
    }

    /** 渲染载荷：模板为空时使用默认载荷；非空时渲染后必须是合法 JSON。 */
    private String renderPayload(String template, RuleExecution execution, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return defaultPayload(execution, values);
        }
        String rendered = RuleTemplateRenderer.render(template, values);
        requireJson(rendered, "载荷模板");
        return rendered;
    }

    /** 默认载荷（T-19 设计文档 §5.3）。 */
    private String defaultPayload(RuleExecution execution, Map<String, String> values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", RuleConstants.EVENT_RULE_TRIGGERED);
        payload.put("ruleId", execution.getRuleId());
        payload.put("ruleName", execution.getRuleName());
        payload.put("executionId", execution.getId());
        payload.put("deviceKey", execution.getDeviceKey());
        payload.put("deviceName", execution.getDeviceName());
        payload.put("deviceType", values.get("deviceType"));
        payload.put("sourceType", execution.getSourceType());
        payload.put("identifier", execution.getIdentifier());
        payload.put("value", execution.getTriggerValue());
        payload.put("reportedAt", values.get("reportedAt"));
        payload.put("timestamp", values.get("timestamp"));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ActionFailure("默认载荷序列化失败");
        }
    }

    /** 渲染 JSON 配置（对象或对象文本）：渲染结果必须是合法 JSON 对象。 */
    private String renderJson(JsonNode node, Map<String, String> values, String label) {
        if (node == null || node.isNull()) {
            return "{}";
        }
        String raw = node.isTextual() ? node.asText() : node.toString();
        String rendered = RuleTemplateRenderer.render(raw, values);
        requireJson(rendered, label);
        return rendered;
    }

    private void requireJson(String text, String label) {
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node == null || node.isMissingNode()) {
                throw new ActionFailure(label + "渲染结果不是合法 JSON");
            }
        } catch (ActionFailure e) {
            throw e;
        } catch (Exception e) {
            throw new ActionFailure(label + "渲染结果不是合法 JSON");
        }
    }

    private Map<String, String> renderHeaders(JsonNode headers, Map<String, String> values) {
        if (headers == null || !headers.isObject()) {
            return Map.of();
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : headers.properties()) {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull() || value.isObject() || value.isArray()) {
                continue;
            }
            rendered.put(entry.getKey(), RuleTemplateRenderer.render(value.asText(), values));
        }
        return rendered;
    }

    private Map<String, String> buildPlaceholders(RuleExecution execution, RuleDefinition rule, Device device) {
        LocalDateTime reportedAt = execution.getCreatedAt() == null ? LocalDateTime.now() : execution.getCreatedAt();
        Map<String, String> values = new HashMap<>();
        values.put("ruleId", String.valueOf(rule.getId()));
        values.put("ruleName", nullToEmpty(rule.getName()));
        values.put("executionId", String.valueOf(execution.getId()));
        values.put("deviceId", String.valueOf(device.getId()));
        values.put("deviceKey", nullToEmpty(device.getDeviceKey()));
        values.put("deviceName", nullToEmpty(device.getDeviceName()));
        values.put("deviceType", nullToEmpty(device.getDeviceType()));
        values.put("sourceType", nullToEmpty(execution.getSourceType()));
        values.put("identifier", nullToEmpty(execution.getIdentifier()));
        values.put("value", nullToEmpty(execution.getTriggerValue()));
        values.put("reportedAt", reportedAt.format(TIME_FORMAT));
        values.put("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        return values;
    }

    private void markSuccess(RuleExecution execution) {
        try {
            executionMapper.markSuccess(execution.getId(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("规则执行记录置成功异常: executionId={}", execution.getId(), e);
        }
    }

    /** 失败处理：未达上限则退避重试，否则置终态 {@code FAILED}。 */
    private void scheduleRetryOrFail(RuleExecution execution, String reason) {
        int current = execution.getAttemptCount() == null ? 0 : execution.getAttemptCount();
        int nextAttempt = current + 1;
        LocalDateTime now = LocalDateTime.now();
        try {
            if (nextAttempt < Math.max(1, properties.getRetryMaxAttempts())) {
                long delayMs = backoffMillis(nextAttempt);
                executionMapper.markRetry(execution.getId(), nextAttempt,
                        now.plusNanos(delayMs * 1_000_000L), reason);
                log.info("规则动作失败，{} ms 后重试（第 {} 次）: executionId={}, 原因={}",
                        delayMs, nextAttempt, execution.getId(), reason);
            } else {
                executionMapper.markFailed(execution.getId(), nextAttempt, reason, now);
                log.warn("规则动作重试耗尽，置失败: executionId={}, 原因={}", execution.getId(), reason);
            }
        } catch (Exception e) {
            log.warn("规则执行记录状态更新异常: executionId={}", execution.getId(), e);
        }
    }

    /** 指数退避：{@code min(base × 2^(attempt-1), max)}，逐步翻倍并封顶，避免长位移溢出。 */
    private long backoffMillis(int attempt) {
        long base = Math.max(1L, properties.getRetryBaseDelayMs());
        long max = Math.max(base, properties.getRetryMaxDelayMs());
        long delay = base;
        for (int i = 1; i < attempt && delay < max; i++) {
            delay = Math.min(delay * 2, max);
        }
        return Math.min(delay, max);
    }

    private void updateForwardPayload(Long executionId, String payload) {
        try {
            executionMapper.updateForwardPayload(executionId, payload);
        } catch (Exception e) {
            log.warn("规则转发载荷快照回填失败: executionId={}", executionId, e);
        }
    }

    /** 回填「最近触发」展示字段（失败只记 WARN，不阻断执行）。 */
    private void touchLastTriggered(Long ruleId) {
        if (ruleId == null) {
            return;
        }
        try {
            definitionMapper.touchLastTriggered(ruleId, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("规则最近触发时间回填失败: ruleId={}", ruleId, e);
        }
    }

    private JsonNode readConfig(RuleDefinition rule) {
        String json = rule.getActionConfig();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            log.warn("规则动作配置解析失败: ruleId={}", rule.getId(), e);
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static int intValue(JsonNode node, int fallback) {
        return node != null && node.isNumber() ? node.asInt() : fallback;
    }

    private static String describe(Throwable e) {
        if (e == null) {
            return "未知异常";
        }
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 动作失败（携带可读原因），由 {@link #execute} 统一转为重试 / 终态。 */
    private static final class ActionFailure extends RuntimeException {

        ActionFailure(String message) {
            super(message);
        }
    }
}