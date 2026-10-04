package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import com.mqtt.cloud.mqtt.RuleMqttForwarder;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.RuleHttpForwarder;
import com.mqtt.cloud.service.SceneActionExecutor;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.util.RuleTemplateRenderer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * 场景步骤动作执行实现（T-23 设计文档 §8.6 / §8.7 / §8.8）。
 * <p>
 * 以 {@code claimPending} 条件更新为**唯一抢占入口**：抢占失败（返回 0）直接退出，保证同一步骤
 * 在多线程 / 多副本下「至少推进一次」而不会重复执行。四类动作复用 T-19 组件：
 * {@code UPDATE_PROPERTY}（{@code upsertIfNewer} 时间戳守卫）、{@code SEND_COMMAND}（命令链路，
 * 强制 {@code async} + {@code source=SCENE}）、{@code FORWARD_MQTT} / {@code FORWARD_HTTP}（独立出站 + HMAC 签名）。
 * <p>
 * 多设备步骤逐台执行、逐台隔离异常，**全部成功才算成功**；部分失败汇总 {@code M/N 成功} 作为失败原因。
 * 成功后推进执行进度并按下一步 {@code delaySeconds} 排期（{@code 0} 可立即投递）；失败按 §8.8 退避重试，
 * 次数耗尽由 {@link SceneExecutionFinalizer} 统一中止（失败步骤 + 后续 {@code SKIPPED} + 执行 {@code FAILED}）。
 * <p>
 * 转发类步骤在渲染成功后**无论发送成功与否**都回填 {@code forward_payload} 快照，便于排障。
 * 全程异常自行兜底，**绝不冒泡**到线程池 / 巡检线程。
 */
@Slf4j
@Service
public class SceneActionExecutorImpl implements SceneActionExecutor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int ERROR_MESSAGE_MAX = 512;
    private static final String HEADER_KEY = "headers";
    private static final String HEADER_STEP_SEQ = "X-Step-Seq";

    private final SceneStepRunMapper stepRunMapper;
    private final SceneStepMapper stepMapper;
    private final SceneDefinitionMapper definitionMapper;
    private final SceneExecutionMapper executionMapper;
    private final DeviceMapper deviceMapper;
    private final DevicePropertyLatestMapper propertyLatestMapper;
    private final DeviceCommandService commandService;
    private final ThingModelService thingModelService;
    private final DeviceBatchService deviceBatchService;
    private final RuleMqttForwarder mqttForwarder;
    private final RuleHttpForwarder httpForwarder;
    private final SceneExecutionFinalizer finalizer;
    private final SceneProperties properties;
    private final RuleProperties ruleProperties;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor sceneExecutor;
    private final Counter rejectedCounter;

    public SceneActionExecutorImpl(SceneStepRunMapper stepRunMapper,
                                   SceneStepMapper stepMapper,
                                   SceneDefinitionMapper definitionMapper,
                                   SceneExecutionMapper executionMapper,
                                   DeviceMapper deviceMapper,
                                   DevicePropertyLatestMapper propertyLatestMapper,
                                   DeviceCommandService commandService,
                                   ThingModelService thingModelService,
                                   DeviceBatchService deviceBatchService,
                                   RuleMqttForwarder mqttForwarder,
                                   RuleHttpForwarder httpForwarder,
                                   SceneExecutionFinalizer finalizer,
                                   SceneProperties properties,
                                   RuleProperties ruleProperties,
                                   ObjectMapper objectMapper,
                                   @Qualifier("sceneExecutor") ThreadPoolTaskExecutor sceneExecutor,
                                   MeterRegistry meterRegistry) {
        this.stepRunMapper = stepRunMapper;
        this.stepMapper = stepMapper;
        this.definitionMapper = definitionMapper;
        this.executionMapper = executionMapper;
        this.deviceMapper = deviceMapper;
        this.propertyLatestMapper = propertyLatestMapper;
        this.commandService = commandService;
        this.thingModelService = thingModelService;
        this.deviceBatchService = deviceBatchService;
        this.mqttForwarder = mqttForwarder;
        this.httpForwarder = httpForwarder;
        this.finalizer = finalizer;
        this.properties = properties;
        this.ruleProperties = ruleProperties;
        this.objectMapper = objectMapper;
        this.sceneExecutor = sceneExecutor;
        this.rejectedCounter = Counter.builder("scene_rejected_total")
                .description("场景步骤投递被线程池拒绝次数")
                .register(meterRegistry);
    }

    @Override
    public void executeStep(Long stepRunId) {
        if (stepRunId == null) {
            return;
        }
        SceneStepRun run;
        try {
            if (stepRunMapper.claimPending(stepRunId) != 1) {
                return;
            }
            run = stepRunMapper.selectById(stepRunId);
            if (run == null) {
                return;
            }
            executionMapper.markRunning(run.getExecutionId(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("场景步骤抢占异常: stepRunId={}", stepRunId, e);
            return;
        }
        try {
            StepContext context = loadContext(run);
            run(run, context);
            markStepSuccess(run);
        } catch (ActionFailure e) {
            handleFailure(run, truncate(e.getMessage(), ERROR_MESSAGE_MAX));
        } catch (Exception e) {
            log.warn("场景步骤执行异常: stepRunId={}", stepRunId, e);
            handleFailure(run, truncate(describe(e), ERROR_MESSAGE_MAX));
        }
    }

    /** 加载执行上下文：执行记录 / 步骤 / 场景 / 触发设备类型；任一缺失即视为不可执行。 */
    private StepContext loadContext(SceneStepRun run) {
        SceneExecution execution = executionMapper.selectById(run.getExecutionId());
        if (execution == null) {
            throw new ActionFailure("执行记录不存在");
        }
        SceneStep step = stepMapper.selectById(run.getStepId());
        if (step == null) {
            throw new ActionFailure("场景或步骤已不存在");
        }
        SceneDefinition scene = definitionMapper.selectById(run.getSceneId());
        if (scene == null) {
            throw new ActionFailure("场景或步骤已不存在");
        }
        String triggerDeviceType = null;
        if (execution.getTriggerDeviceId() != null) {
            Device trigger = deviceMapper.selectById(execution.getTriggerDeviceId());
            if (trigger != null) {
                triggerDeviceType = trigger.getDeviceType();
            }
        }
        return new StepContext(scene, step, execution, triggerDeviceType);
    }

    /** 分派动作：转发类单次出站；属性 / 命令类逐台执行且全部成功才算成功。 */
    private void run(SceneStepRun run, StepContext context) {
        SceneStep step = context.step();
        SceneExecution execution = context.execution();
        SceneDefinition scene = context.scene();
        JsonNode config = readConfig(step);
        if (config == null) {
            throw new ActionFailure("动作配置缺失或非法");
        }
        String actionType = step.getActionType();

        if (SceneConstants.FORWARD_ACTIONS.contains(actionType)) {
            Map<String, String> values = buildPlaceholders(context, run, null);
            switch (actionType) {
                case SceneConstants.ACTION_FORWARD_MQTT -> forwardMqtt(run, config, values);
                case SceneConstants.ACTION_FORWARD_HTTP -> forwardHttp(run, scene, config, values);
                default -> throw new ActionFailure("不支持的动作类型 " + actionType);
            }
            return;
        }

        List<Device> devices = resolveDevices(step, execution);
        if (devices.isEmpty()) {
            throw new ActionFailure("目标设备为空");
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (Device device : devices) {
            Map<String, String> values = buildPlaceholders(context, run, device);
            try {
                switch (actionType) {
                    case SceneConstants.ACTION_UPDATE_PROPERTY -> updateProperty(device, config, values);
                    case SceneConstants.ACTION_SEND_COMMAND -> sendCommand(device, config, values);
                    default -> throw new ActionFailure("不支持的动作类型 " + actionType);
                }
                success++;
            } catch (Exception e) {
                errors.add(nullToEmpty(device.getDeviceKey()) + ": " + describe(e));
            }
        }
        if (success < devices.size()) {
            String summary = success + "/" + devices.size() + " 成功";
            if (!errors.isEmpty()) {
                summary = summary + "：" + String.join("; ", errors);
            }
            throw new ActionFailure(summary);
        }
    }

    /** 解析目标设备：{@code TRIGGER} 触发设备（执行期二次校验归属）；{@code FIXED} 复用 T-18 目标解析。 */
    private List<Device> resolveDevices(SceneStep step, SceneExecution execution) {
        if (SceneConstants.TARGET_TRIGGER.equals(step.getTargetType())) {
            Device trigger = execution.getTriggerDeviceId() == null
                    ? null : deviceMapper.selectById(execution.getTriggerDeviceId());
            if (trigger == null) {
                throw new ActionFailure("触发设备不存在或已删除");
            }
            if (execution.getUserId() == null || !execution.getUserId().equals(trigger.getOwnerId())) {
                throw new ActionFailure("目标设备归属已变更");
            }
            return List.of(trigger);
        }
        BatchTargetRequest target = readTarget(step.getTargetConfig());
        if (target == null) {
            throw new ActionFailure("固定目标配置缺失或非法");
        }
        try {
            List<Device> devices = deviceBatchService.resolveTarget(execution.getUserId(), target);
            return devices == null ? List.of() : devices;
        } catch (BusinessException e) {
            throw new ActionFailure(e.getMessage());
        } catch (Exception e) {
            throw new ActionFailure("目标解析失败：" + describe(e));
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
     * 下发命令：复用命令链路（含物模型校验、影子写入、离线入队、超时巡检），强制 {@code async} + {@code source=SCENE}。
     * <p>
     * {@code property_set} 的目标属性随 {@code params} 承载；配置中的 {@code identifier} 只用于保存期校验。
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
                    DeviceCommandService.SOURCE_SCENE,
                    null));
        } catch (Exception e) {
            throw new ActionFailure("下发命令失败：" + describe(e));
        }
    }

    /** 转发外部 MQTT：渲染 Topic / 载荷后出站到第三方 Broker，渲染成功即回填载荷快照。 */
    private void forwardMqtt(SceneStepRun run, JsonNode config, Map<String, String> values) {
        String topic = RuleTemplateRenderer.render(text(config, "topic"), values);
        if (topic == null || topic.isBlank()) {
            throw new ActionFailure("转发 MQTT 缺少 Topic");
        }
        int qos = intValue(config.get("qos"), ruleProperties.getMqtt().getQos());
        String payload = renderPayload(text(config, "payloadTemplate"), run, values);
        updateForwardPayload(run.getId(), payload);
        try {
            mqttForwarder.publish(topic, payload, qos);
        } catch (Exception e) {
            throw new ActionFailure("MQTT 转发失败：" + describe(e));
        }
    }

    /** 转发 HTTP：渲染 URL / 请求头 / 载荷后出站 POST/PUT，渲染成功即回填载荷快照。 */
    private void forwardHttp(SceneStepRun run, SceneDefinition scene, JsonNode config, Map<String, String> values) {
        String url = RuleTemplateRenderer.render(text(config, "url"), values);
        if (url == null || url.isBlank()) {
            throw new ActionFailure("转发 HTTP 缺少 URL");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ActionFailure("URL 必须以 http:// 或 https:// 开头");
        }
        String method = text(config, "method");
        Map<String, String> headers = renderHeaders(config.get(HEADER_KEY), values);
        headers.put(HEADER_STEP_SEQ, String.valueOf(run.getSeq()));
        String payload = renderPayload(text(config, "payloadTemplate"), run, values);
        updateForwardPayload(run.getId(), payload);
        try {
            httpForwarder.send(SceneConstants.EVENT_SCENE_TRIGGERED, SceneConstants.HEADER_SCENE_ID, scene.getId(),
                    method, url, headers, payload, run.getExecutionId());
        } catch (Exception e) {
            throw new ActionFailure("HTTP 转发失败：" + describe(e));
        }
    }

    /** 渲染载荷：模板为空时使用默认载荷；非空时渲染后必须是合法 JSON。 */
    private String renderPayload(String template, SceneStepRun run, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return defaultPayload(run, values);
        }
        String rendered = RuleTemplateRenderer.render(template, values);
        requireJson(rendered, "载荷模板");
        return rendered;
    }

    /** 默认载荷（T-23 设计文档 §5.4）。 */
    private String defaultPayload(SceneStepRun run, Map<String, String> values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", SceneConstants.EVENT_SCENE_TRIGGERED);
        payload.put("sceneId", run.getSceneId());
        payload.put("sceneName", values.get("sceneName"));
        payload.put("executionId", run.getExecutionId());
        payload.put("stepSeq", run.getSeq());
        payload.put("triggerType", values.get("triggerType"));
        payload.put("triggerDeviceKey", values.get("triggerDeviceKey"));
        payload.put("triggerDeviceName", values.get("triggerDeviceName"));
        payload.put("triggerDeviceType", values.get("triggerDeviceType"));
        payload.put("triggerIdentifier", values.get("triggerIdentifier"));
        payload.put("triggerValue", emptyToNull(values.get("triggerValue")));
        payload.put("triggeredAt", values.get("triggeredAt"));
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
        Map<String, String> rendered = new LinkedHashMap<>();
        if (headers == null || !headers.isObject()) {
            return rendered;
        }
        for (Map.Entry<String, JsonNode> entry : headers.properties()) {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull() || value.isObject() || value.isArray()) {
                continue;
            }
            rendered.put(entry.getKey(), RuleTemplateRenderer.render(value.asText(), values));
        }
        return rendered;
    }

    /** 构建占位符取值（T-23 设计文档 §8.10）；{@code targetDevice} 为空时目标占位符渲染为空串。 */
    private Map<String, String> buildPlaceholders(StepContext context, SceneStepRun run, Device targetDevice) {
        SceneDefinition scene = context.scene();
        SceneExecution execution = context.execution();
        Map<String, String> values = new HashMap<>();
        values.put("sceneId", String.valueOf(scene.getId()));
        values.put("sceneName", nullToEmpty(scene.getName()));
        values.put("executionId", String.valueOf(execution.getId()));
        values.put("stepSeq", String.valueOf(run.getSeq()));
        values.put("triggerType", nullToEmpty(execution.getTriggerType()));
        values.put("triggerDeviceId", numToEmpty(execution.getTriggerDeviceId()));
        values.put("triggerDeviceKey", nullToEmpty(execution.getTriggerDeviceKey()));
        values.put("triggerDeviceName", nullToEmpty(execution.getTriggerDeviceName()));
        values.put("triggerDeviceType", nullToEmpty(context.triggerDeviceType()));
        values.put("triggerIdentifier", nullToEmpty(execution.getTriggerIdentifier()));
        values.put("triggerValue", nullToEmpty(execution.getTriggerValue()));
        values.put("triggeredAt", formatTime(execution.getCreatedAt()));
        values.put("targetDeviceId", targetDevice == null ? "" : String.valueOf(targetDevice.getId()));
        values.put("targetDeviceKey", targetDevice == null ? "" : nullToEmpty(targetDevice.getDeviceKey()));
        values.put("targetDeviceName", targetDevice == null ? "" : nullToEmpty(targetDevice.getDeviceName()));
        values.put("targetDeviceType", targetDevice == null ? "" : nullToEmpty(targetDevice.getDeviceType()));
        values.put("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        return values;
    }

    /** 成功收口：置步骤成功 + 推进进度；有下一步则排期（延时 0 立即投递），末步则整条执行成功。 */
    private void markStepSuccess(SceneStepRun run) {
        LocalDateTime finishedAt = LocalDateTime.now();
        try {
            stepRunMapper.markSuccess(run.getId(), finishedAt);
            executionMapper.bumpFinishedSteps(run.getExecutionId(), 1);
        } catch (Exception e) {
            log.warn("场景步骤置成功异常: stepRunId={}", run.getId(), e);
            return;
        }
        try {
            SceneStepRun next = findNext(run);
            if (next == null) {
                executionMapper.markSuccess(run.getExecutionId(), finishedAt);
                return;
            }
            int delay = next.getDelaySeconds() == null ? 0 : next.getDelaySeconds();
            LocalDateTime scheduledAt = LocalDateTime.now().plusSeconds(delay);
            stepRunMapper.scheduleNext(next.getId(), scheduledAt, scheduledAt);
            if (delay == 0) {
                deliver(next.getId());
            }
        } catch (Exception e) {
            log.warn("场景下一步排期异常: executionId={}, seq={}", run.getExecutionId(), run.getSeq(), e);
        }
    }

    /** 找该执行下序号大于当前步的最小步骤（下一步）。 */
    private SceneStepRun findNext(SceneStepRun run) {
        if (run.getSeq() == null) {
            return null;
        }
        SceneStepRun next = null;
        for (SceneStepRun sibling : stepRunMapper.selectByExecutionId(run.getExecutionId())) {
            if (sibling.getSeq() == null || sibling.getSeq() <= run.getSeq()) {
                continue;
            }
            if (next == null || sibling.getSeq() < next.getSeq()) {
                next = sibling;
            }
        }
        return next;
    }

    /** 投递步骤到场景线程池（只投递不执行）；队列满则置该步失败并中止整条执行。 */
    private void deliver(Long stepRunId) {
        try {
            sceneExecutor.execute(() -> executeStep(stepRunId));
        } catch (RejectedExecutionException e) {
            rejectedCounter.increment();
            log.warn("场景下一步投递被拒绝（执行队列已满）: stepRunId={}", stepRunId);
            markRejected(stepRunId);
        } catch (Exception e) {
            log.warn("场景下一步投递异常: stepRunId={}", stepRunId, e);
        }
    }

    private void markRejected(Long stepRunId) {
        try {
            SceneStepRun run = stepRunMapper.selectById(stepRunId);
            if (run == null) {
                return;
            }
            if (stepRunMapper.claimPending(stepRunId) == 1) {
                finalizer.abort(run, 1, "执行队列已满");
            }
        } catch (Exception e) {
            log.warn("场景步骤拒绝收口异常: stepRunId={}", stepRunId, e);
        }
    }

    /**
     * 失败处理（§8.8）：{@code attempt_count < retry-max-attempts} 时按指数退避重设锚点回 {@code PENDING}，
     * 否则交由 {@link SceneExecutionFinalizer} 中止（失败步骤 + 后续 {@code SKIPPED} + 执行 {@code FAILED}）。
     */
    private void handleFailure(SceneStepRun run, String reason) {
        try {
            int current = run.getAttemptCount() == null ? 0 : run.getAttemptCount();
            int nextAttempt = current + 1;
            LocalDateTime now = LocalDateTime.now();
            if (current < Math.max(1, properties.getRetryMaxAttempts())) {
                long delayMs = backoffMillis(nextAttempt);
                stepRunMapper.markRetry(run.getId(), nextAttempt, now.plusNanos(delayMs * 1_000_000L), reason);
                log.info("场景步骤失败，{} ms 后重试（第 {} 次）: stepRunId={}, 原因={}",
                        delayMs, nextAttempt, run.getId(), reason);
            } else {
                finalizer.abort(run, nextAttempt, reason);
            }
        } catch (Exception e) {
            log.warn("场景步骤状态更新异常: stepRunId={}", run.getId(), e);
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

    private void updateForwardPayload(Long stepRunId, String payload) {
        try {
            stepRunMapper.updateForwardPayload(stepRunId, payload);
        } catch (Exception e) {
            log.warn("场景转发载荷快照回填失败: stepRunId={}", stepRunId, e);
        }
    }

    private JsonNode readConfig(SceneStep step) {
        String json = step.getActionConfig();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            log.warn("场景步骤动作配置解析失败: stepId={}", step.getId(), e);
            return null;
        }
    }

    private BatchTargetRequest readTarget(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BatchTargetRequest.class);
        } catch (Exception e) {
            log.warn("场景固定目标解析失败: {}", e.getMessage());
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

    private static String numToEmpty(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String formatTime(LocalDateTime time) {
        return (time == null ? LocalDateTime.now() : time).format(TIME_FORMAT);
    }

    /** 步骤执行上下文：场景 / 步骤 / 执行记录 / 触发设备类型快照。 */
    private record StepContext(SceneDefinition scene,
                               SceneStep step,
                               SceneExecution execution,
                               String triggerDeviceType) {
    }

    /** 动作失败（携带可读原因），由 {@link #executeStep} 统一转为重试 / 终态。 */
    private static final class ActionFailure extends RuntimeException {

        ActionFailure(String message) {
            super(message);
        }
    }
}