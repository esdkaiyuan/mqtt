package com.mqtt.cloud.util;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.constant.ShadowChangeSide;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.request.SceneConditionRequest;
import com.mqtt.cloud.dto.request.SceneRequest;
import com.mqtt.cloud.dto.request.SceneStepRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景保存期校验（T-23 设计文档 §7）。
 * <p>
 * 集中触发源 / 条件组 / 步骤 / 目标 / 动作配置的校验口径，失败抛对应错误码：
 * 非法配置 {@code 6241}、非法动作类型 {@code 6242}、非法触发源 {@code 6245}、超上限 {@code 6244}、
 * 越权设备 {@code 2003}、目标集合非法 {@code 6217}（复用 T-18 既有码）。
 * <p>
 * 动作配置形状与比较符口径**复用 {@link RuleConstants}**（与 T-19 一致）；目标集合归属校验复用
 * {@link DeviceBatchService#resolveTarget(Long, BatchTargetRequest)}（含分组 / 标签归属与上限校验）。
 * 名称唯一性与场景数上限依赖 Mapper，留在 {@code SceneServiceImpl}。
 * <p>
 * 保存期**不查询物模型**（设备可能尚未建模），标识符存在性留待执行时校验与试运行诊断。
 */
@Slf4j
@Component
public class SceneValidator {

    private final DeviceService deviceService;
    private final DeviceBatchService deviceBatchService;
    private final SceneProperties sceneProperties;
    private final RuleProperties ruleProperties;
    private final ObjectMapper objectMapper;

    public SceneValidator(DeviceService deviceService,
                          DeviceBatchService deviceBatchService,
                          SceneProperties sceneProperties,
                          RuleProperties ruleProperties,
                          ObjectMapper objectMapper) {
        this.deviceService = deviceService;
        this.deviceBatchService = deviceBatchService;
        this.sceneProperties = sceneProperties;
        this.ruleProperties = ruleProperties;
        this.objectMapper = objectMapper;
    }

    /** 校验结果：规范化后的场景定义（不含 id / userId）与步骤列表（不含 sceneId）。 */
    public record ValidatedScene(SceneDefinition definition, List<SceneStep> steps) {
    }

    /**
     * 校验请求并产出可直接落库的场景定义与步骤。
     *
     * @param userId 当前用户（设备 / 目标归属校验）
     * @param request 场景创建 / 更新请求
     */
    public ValidatedScene validate(Long userId, SceneRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空");
        }

        String name = requireText(request.getName(), "场景名称不能为空");
        if (name.length() > RuleConstants.MAX_NAME_LENGTH) {
            throw invalid("场景名称长度不能超过 " + RuleConstants.MAX_NAME_LENGTH);
        }
        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > RuleConstants.MAX_DESCRIPTION_LENGTH) {
            throw invalid("描述长度不能超过 " + RuleConstants.MAX_DESCRIPTION_LENGTH);
        }

        String triggerType = normalizeUpper(request.getTriggerType());
        if (triggerType == null || !SceneConstants.TRIGGERS.contains(triggerType)) {
            throw new BusinessException(ResultCode.SCENE_TRIGGER_UNSUPPORTED,
                    "触发源必须是 PROPERTY / EVENT / TIMER / ONLINE / OFFLINE");
        }

        Long triggerDeviceId = request.getTriggerDeviceId();
        if (triggerDeviceId != null) {
            checkDeviceOwned(userId, triggerDeviceId);
        }

        int cooldownSeconds = request.getCooldownSeconds() == null ? 0 : request.getCooldownSeconds();
        if (cooldownSeconds < 0 || cooldownSeconds > RuleConstants.MAX_COOLDOWN_SECONDS) {
            throw invalid("冷却窗口取值需在 [0, " + RuleConstants.MAX_COOLDOWN_SECONDS + "] 秒");
        }

        String conditionLogic = normalizeUpper(request.getConditionLogic());
        if (conditionLogic == null) {
            conditionLogic = SceneConstants.LOGIC_AND;
        } else if (!SceneConstants.LOGICS.contains(conditionLogic)) {
            throw invalid("条件组合必须是 AND / OR");
        }

        Trigger trigger = validateTrigger(userId, triggerType, request);
        String conditionConfigJson = validateConditions(userId, triggerType, request.getConditions());
        List<SceneStep> steps = validateSteps(userId, triggerType, request.getSteps());

        SceneDefinition definition = new SceneDefinition();
        definition.setName(name);
        definition.setDescription(description);
        definition.setTriggerType(triggerType);
        definition.setTriggerDeviceId(trigger.triggerDeviceId());
        definition.setTriggerIdentifier(trigger.triggerIdentifier());
        definition.setTriggerOperator(trigger.triggerOperator());
        definition.setTriggerThreshold(trigger.triggerThreshold());
        definition.setTriggerEventType(trigger.triggerEventType());
        definition.setTriggerShadowSide(trigger.shadowSide());
        definition.setTimerCron(trigger.timerCron());
        definition.setConditionLogic(conditionLogic);
        definition.setConditionConfig(conditionConfigJson);
        definition.setCooldownSeconds(cooldownSeconds);
        definition.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        return new ValidatedScene(definition, steps);
    }

    // ---------- 触发源 ----------

    private record Trigger(Long triggerDeviceId, String triggerIdentifier, String triggerOperator,
                           String triggerThreshold, String triggerEventType, String timerCron,
                           String shadowSide) {
    }

    private Trigger validateTrigger(Long userId, String triggerType, SceneRequest request) {
        String identifier = trimToNull(request.getTriggerIdentifier());
        String operator = normalizeUpper(request.getTriggerOperator());
        String threshold = trimToNull(request.getTriggerThreshold());
        String eventType = trimToNull(request.getTriggerEventType());
        String cron = trimToNull(request.getTimerCron());
        String shadowSide = normalizeUpper(request.getTriggerShadowSide());
        Long deviceId = request.getTriggerDeviceId();

        if (SceneConstants.TRIGGER_TIMER.equals(triggerType)) {
            if (cron == null) {
                throw invalid("定时触发必须填写 cron 表达式");
            }
            try {
                SceneCronSupport.parse(cron);
            } catch (IllegalArgumentException e) {
                throw invalid("cron 表达式非法：" + e.getMessage());
            }
            if (deviceId != null || identifier != null || operator != null || threshold != null
                    || eventType != null || shadowSide != null) {
                throw invalid("定时触发不允许配置触发设备 / 标识符 / 比较条件 / 事件类型 / 影子变更侧");
            }
            return new Trigger(null, null, null, null, null, cron, null);
        }

        if (cron != null) {
            throw invalid("cron 表达式仅在定时触发时允许配置");
        }

        if (SceneConstants.STATUS_TRIGGERS.contains(triggerType)) {
            if (identifier != null || operator != null || threshold != null || eventType != null
                    || shadowSide != null) {
                throw invalid("上下线触发不允许配置标识符 / 比较条件 / 事件类型 / 影子变更侧");
            }
            return new Trigger(deviceId, null, null, null, null, null, null);
        }

        if (SceneConstants.TRIGGER_SHADOW.equals(triggerType)) {
            if (operator != null || threshold != null || eventType != null) {
                throw invalid("影子变更触发不支持比较符 / 阈值 / 事件类型");
            }
            if (shadowSide == null) {
                shadowSide = ShadowChangeSide.REPORTED;
            } else if (!ShadowChangeSide.ALL.contains(shadowSide)) {
                throw invalid("影子变更侧必须是 REPORTED / DESIRED / ANY");
            }
            if (identifier != null && identifier.length() > RuleConstants.MAX_IDENTIFIER_LENGTH) {
                throw invalid("触发标识符长度不能超过 " + RuleConstants.MAX_IDENTIFIER_LENGTH);
            }
            return new Trigger(deviceId, identifier, null, null, null, null, shadowSide);
        }

        if (shadowSide != null) {
            throw invalid("影子变更侧仅用于 SHADOW 触发源");
        }
        if (identifier == null) {
            throw invalid("触发标识符不能为空");
        }
        if (identifier.length() > RuleConstants.MAX_IDENTIFIER_LENGTH) {
            throw invalid("触发标识符长度不能超过 " + RuleConstants.MAX_IDENTIFIER_LENGTH);
        }

        if (SceneConstants.TRIGGER_EVENT.equals(triggerType)) {
            if (operator != null || threshold != null) {
                throw invalid("事件触发不支持比较符与阈值");
            }
            if (eventType != null) {
                eventType = eventType.toLowerCase();
                if (eventType.length() > RuleConstants.MAX_EVENT_TYPE_LENGTH) {
                    throw invalid("事件类型长度不能超过 " + RuleConstants.MAX_EVENT_TYPE_LENGTH);
                }
            }
            return new Trigger(deviceId, identifier, null, null, eventType, null, null);
        }

        // PROPERTY
        if ((operator == null) != (threshold == null)) {
            throw invalid("触发比较符与阈值必须同时填写或同时留空");
        }
        if (operator != null) {
            if (!RuleConstants.OPERATORS.contains(operator)) {
                throw invalid("触发比较符必须是 GT / GTE / LT / LTE / EQ / NE");
            }
            if (threshold.length() > RuleConstants.MAX_THRESHOLD_LENGTH) {
                throw invalid("触发阈值长度不能超过 " + RuleConstants.MAX_THRESHOLD_LENGTH);
            }
            if (SceneConstants.NUMERIC_OPERATORS.contains(operator) && !isDecimal(threshold)) {
                throw invalid("数值比较符下触发阈值必须是可解析的十进制数");
            }
        }
        return new Trigger(deviceId, identifier, operator, threshold, null, null, null);
    }

    // ---------- 条件组 ----------

    private String validateConditions(Long userId, String triggerType, List<SceneConditionRequest> conditions) {
        List<SceneConditionRequest> items = conditions == null ? List.of() : conditions;
        if (items.size() > sceneProperties.getMaxConditionsPerScene()) {
            throw new BusinessException(ResultCode.SCENE_LIMIT_EXCEEDED,
                    "条件项数不能超过 " + sceneProperties.getMaxConditionsPerScene());
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (SceneConditionRequest item : items) {
            if (item == null) {
                continue;
            }
            String identifier = requireText(item.getIdentifier(), "条件项标识符不能为空");
            if (identifier.length() > RuleConstants.MAX_IDENTIFIER_LENGTH) {
                throw invalid("条件项标识符长度不能超过 " + RuleConstants.MAX_IDENTIFIER_LENGTH);
            }
            String operator = normalizeUpper(item.getOperator());
            if (operator == null || !RuleConstants.OPERATORS.contains(operator)) {
                throw invalid("条件项比较符必须是 GT / GTE / LT / LTE / EQ / NE");
            }
            String threshold = requireText(item.getThreshold(), "条件项阈值不能为空");
            if (threshold.length() > RuleConstants.MAX_THRESHOLD_LENGTH) {
                throw invalid("条件项阈值长度不能超过 " + RuleConstants.MAX_THRESHOLD_LENGTH);
            }
            if (SceneConstants.NUMERIC_OPERATORS.contains(operator) && !isDecimal(threshold)) {
                throw invalid("数值比较符下条件项阈值必须是可解析的十进制数");
            }
            Long deviceId = item.getDeviceId();
            if (SceneConstants.TRIGGER_TIMER.equals(triggerType) && deviceId == null) {
                throw invalid("定时触发的条件项必须指定取值设备");
            }
            if (deviceId != null) {
                checkDeviceOwned(userId, deviceId);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("deviceId", deviceId);
            entry.put("identifier", identifier);
            entry.put("operator", operator);
            entry.put("threshold", threshold);
            normalized.add(entry);
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw invalid("条件组序列化失败");
        }
    }

    // ---------- 步骤 ----------

    private List<SceneStep> validateSteps(Long userId, String triggerType, List<SceneStepRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            throw invalid("场景至少需要一个步骤");
        }
        if (steps.size() > sceneProperties.getMaxStepsPerScene()) {
            throw new BusinessException(ResultCode.SCENE_LIMIT_EXCEEDED,
                    "步骤数不能超过 " + sceneProperties.getMaxStepsPerScene());
        }

        List<SceneStep> result = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            SceneStepRequest step = steps.get(i);
            int expectedSeq = i + 1;
            if (step == null) {
                throw invalid("第 " + expectedSeq + " 个步骤不能为空");
            }
            if (step.getSeq() == null || step.getSeq() != expectedSeq) {
                throw invalid("步骤序号必须从 1 开始且连续（期望 " + expectedSeq + "）");
            }

            int delay = step.getDelaySeconds() == null ? 0 : step.getDelaySeconds();
            if (delay < 0 || delay > sceneProperties.getMaxStepDelaySeconds()) {
                throw invalid("步骤延时取值需在 [0, " + sceneProperties.getMaxStepDelaySeconds() + "] 秒");
            }

            String actionType = normalizeUpper(step.getActionType());
            if (actionType == null) {
                throw new BusinessException(ResultCode.SCENE_STEP_UNSUPPORTED, "步骤动作类型不能为空");
            }
            if (!SceneConstants.STEP_TYPES.contains(actionType)) {
                throw new BusinessException(ResultCode.SCENE_STEP_UNSUPPORTED, "不支持的步骤动作类型 " + actionType);
            }

            boolean flowStep = SceneConstants.FLOW_STEPS.contains(actionType);
            String targetType = normalizeUpper(step.getTargetType());
            if (flowStep) {
                if (targetType != null && !SceneConstants.TARGET_NONE.equals(targetType)) {
                    throw invalid("流程步骤（分支 / 循环 / 并行）不支持目标配置");
                }
                if (!isEmpty(step.getTargetConfig())) {
                    throw invalid("流程步骤（分支 / 循环 / 并行）不支持目标配置");
                }
                targetType = SceneConstants.TARGET_NONE;
            } else {
                if (targetType == null || !SceneConstants.TARGET_TRIGGER.equals(targetType)
                        && !SceneConstants.TARGET_FIXED.equals(targetType)) {
                    throw invalid("步骤目标类型必须是 TRIGGER / FIXED");
                }
                if (SceneConstants.FORWARD_ACTIONS.contains(actionType)
                        && !SceneConstants.TARGET_TRIGGER.equals(targetType)) {
                    throw invalid("转发类动作的目标类型必须是 TRIGGER");
                }
                if (SceneConstants.TRIGGER_TIMER.equals(triggerType)
                        && (SceneConstants.ACTION_UPDATE_PROPERTY.equals(actionType)
                            || SceneConstants.ACTION_SEND_COMMAND.equals(actionType))
                        && !SceneConstants.TARGET_FIXED.equals(targetType)) {
                    throw invalid("定时触发的属性更新 / 命令下发步骤必须使用固定目标（FIXED）");
                }
            }

            String targetConfigJson = validateTarget(userId, targetType, step.getTargetConfig());
            String actionConfigJson = validateActionConfig(userId, triggerType, actionType, step.getActionConfig());

            SceneStep entity = new SceneStep();
            entity.setSeq(expectedSeq);
            entity.setDelaySeconds(delay);
            entity.setActionType(actionType);
            entity.setTargetType(targetType);
            entity.setTargetConfig(targetConfigJson);
            entity.setActionConfig(actionConfigJson);
            entity.setParallelSeq(flowStep ? null : step.getParallelSeq());
            entity.setBranchIndex(flowStep ? null : step.getBranchIndex());
            entity.setEnabled(step.getEnabled() == null || step.getEnabled() ? 1 : 0);
            result.add(entity);
        }
        validateFlowStructure(result, steps.size());
        return result;
    }

    /** 校验固定目标：非空、至少一个集合非空、归属合法、去重后数量在 [1, min(MAX_BATCH_SIZE, max-target-devices)]。 */
    private String validateTarget(Long userId, String targetType, BatchTargetRequest targetConfig) {
        if (SceneConstants.TARGET_TRIGGER.equals(targetType) || SceneConstants.TARGET_NONE.equals(targetType)) {
            return null;
        }
        if (targetConfig == null || isEmpty(targetConfig)) {
            throw invalid("固定目标必须至少指定一个设备 / 产品 / 分组 / 标签");
        }
        // resolveTarget 内部校验归属（6212 / 6214）与空集 / 超 MAX_BATCH_SIZE（6217）
        List<Device> devices = deviceBatchService.resolveTarget(userId, targetConfig);
        if (devices.size() > sceneProperties.getMaxTargetDevices()) {
            throw invalid("单步骤目标设备数不能超过 " + sceneProperties.getMaxTargetDevices());
        }
        try {
            return objectMapper.writeValueAsString(targetConfig);
        } catch (Exception e) {
            throw invalid("固定目标序列化失败");
        }
    }

    private static boolean isEmpty(BatchTargetRequest target) {
        return target == null || (isBlank(target.getDeviceIds()) && isBlank(target.getProductIds())
                && isBlank(target.getGroupIds()) && isBlank(target.getTagIds()));
    }

    private static boolean isBlank(List<Long> list) {
        return list == null || list.isEmpty();
    }

    // ---------- 动作配置（形状同 T-19 §5.2 / §7.3） ----------

    private String validateActionConfig(Long userId, String triggerType, String actionType, JsonNode config) {
        if (config == null || !config.isObject()) {
            throw invalid("动作配置不能为空");
        }
        switch (actionType) {
            case SceneConstants.ACTION_UPDATE_PROPERTY -> {
                checkIdentifier(text(config, "identifier"), "更新属性动作");
                String value = text(config, "value");
                if (value == null) {
                    throw invalid("更新属性动作必须填写取值");
                }
                if (value.length() > 512) {
                    throw invalid("更新属性动作取值长度不能超过 512");
                }
            }
            case SceneConstants.ACTION_SEND_COMMAND -> {
                String commandType = text(config, "commandType");
                if (!"property_set".equals(commandType) && !"service".equals(commandType)) {
                    throw invalid("命令类型必须是 property_set / service");
                }
                checkIdentifier(text(config, "identifier"), "下发命令动作");
                JsonNode params = config.get("params");
                if (params != null && !params.isNull() && !params.isObject() && !params.isTextual()) {
                    throw invalid("命令参数必须是 JSON 对象或对象文本");
                }
            }
            case SceneConstants.ACTION_FORWARD_MQTT -> {
                String topic = text(config, "topic");
                if (topic == null) {
                    throw invalid("转发 MQTT 动作必须填写 Topic");
                }
                if (topic.length() > RuleConstants.MAX_TOPIC_LENGTH) {
                    throw invalid("Topic 长度不能超过 " + RuleConstants.MAX_TOPIC_LENGTH);
                }
                if (topic.contains("#") || topic.contains("+")) {
                    throw invalid("发布侧 Topic 不允许包含通配符 # / +");
                }
                JsonNode qos = config.get("qos");
                if (qos != null && !qos.isNull()) {
                    if (!qos.isNumber() || !RuleConstants.MQTT_QOS.contains(qos.asInt())) {
                        throw invalid("QoS 必须是 0 / 1 / 2");
                    }
                }
                checkPayloadTemplate(config);
            }
            case SceneConstants.ACTION_FORWARD_HTTP -> {
                String url = text(config, "url");
                if (url == null) {
                    throw invalid("转发 HTTP 动作必须填写 URL");
                }
                if (url.length() > RuleConstants.MAX_URL_LENGTH) {
                    throw invalid("URL 长度不能超过 " + RuleConstants.MAX_URL_LENGTH);
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw invalid("URL 必须以 http:// 或 https:// 开头");
                }
                String method = text(config, "method");
                if (method != null && !RuleConstants.HTTP_METHODS.contains(method.toUpperCase())) {
                    throw invalid("HTTP 方法必须是 POST / PUT");
                }
                JsonNode headers = config.get("headers");
                if (headers != null && !headers.isNull()) {
                    if (!headers.isObject()) {
                        throw invalid("请求头必须是 JSON 对象");
                    }
                    int count = 0;
                    for (Map.Entry<String, JsonNode> entry : headers.properties()) {
                        if (++count > RuleConstants.MAX_HEADERS) {
                            throw invalid("请求头个数不能超过 " + RuleConstants.MAX_HEADERS);
                        }
                        if (entry.getValue().isObject() || entry.getValue().isArray()
                                || entry.getValue().asText().length() > RuleConstants.MAX_HEADER_VALUE_LENGTH) {
                            throw invalid("请求头单值长度不能超过 " + RuleConstants.MAX_HEADER_VALUE_LENGTH);
                        }
                    }
                }
                checkPayloadTemplate(config);
            }
            case SceneConstants.STEP_BRANCH -> {
                checkIdentifier(text(config, "identifier"), "分支条件");
                String operator = normalizeUpper(text(config, "operator"));
                if (operator == null || !RuleConstants.OPERATORS.contains(operator)) {
                    throw invalid("分支条件比较符必须是 GT / GTE / LT / LTE / EQ / NE");
                }
                String threshold = text(config, "threshold");
                if (threshold == null) {
                    throw invalid("分支条件阈值不能为空");
                }
                if (threshold.length() > RuleConstants.MAX_THRESHOLD_LENGTH) {
                    throw invalid("分支条件阈值长度不能超过 " + RuleConstants.MAX_THRESHOLD_LENGTH);
                }
                if (SceneConstants.NUMERIC_OPERATORS.contains(operator) && !isDecimal(threshold)) {
                    throw invalid("数值比较符下分支条件阈值必须是可解析的十进制数");
                }
                Long conditionDeviceId = longOrNull(config, "deviceId");
                if (conditionDeviceId != null) {
                    checkDeviceOwned(userId, conditionDeviceId);
                }
                if (SceneConstants.TRIGGER_TIMER.equals(triggerType) && conditionDeviceId == null) {
                    throw invalid("定时触发的分支条件必须指定取值设备");
                }
                Integer trueSeq = intOrNull(config, "trueSeq");
                Integer falseSeq = intOrNull(config, "falseSeq");
                if (trueSeq == null && falseSeq == null) {
                    throw invalid("分支步骤至少需要一个跳转目标（trueSeq / falseSeq）");
                }
                if ((trueSeq != null && trueSeq < 1) || (falseSeq != null && falseSeq < 1)) {
                    throw invalid("分支跳转目标序号必须从 1 开始");
                }
            }
            case SceneConstants.STEP_LOOP -> {
                Integer startSeq = intOrNull(config, "startSeq");
                Integer times = intOrNull(config, "times");
                if (startSeq == null || startSeq < 1) {
                    throw invalid("循环步骤必须指定循环体起始序号 startSeq");
                }
                if (times == null || times < 1 || times > SceneConstants.MAX_LOOP_TIMES) {
                    throw invalid("循环次数需在 [1, " + SceneConstants.MAX_LOOP_TIMES + "]");
                }
            }
            case SceneConstants.STEP_PARALLEL -> {
                Integer branches = intOrNull(config, "branches");
                if (branches == null || branches < SceneConstants.MIN_PARALLEL_BRANCHES
                        || branches > SceneConstants.MAX_PARALLEL_BRANCHES) {
                    throw invalid("并行分支数需在 [" + SceneConstants.MIN_PARALLEL_BRANCHES
                            + ", " + SceneConstants.MAX_PARALLEL_BRANCHES + "]");
                }
                if (config.properties().size() != 1) {
                    throw invalid("并行开始步骤仅支持 branches 配置");
                }
            }
            case SceneConstants.STEP_JOIN -> {
                if (!config.properties().isEmpty()) {
                    throw invalid("并行汇合步骤不支持配置");
                }
            }
            default -> throw new BusinessException(ResultCode.SCENE_STEP_UNSUPPORTED,
                    "不支持的步骤动作类型 " + actionType);
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw invalid("动作配置序列化失败");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > ruleProperties.getMaxPayloadBytes()) {
            throw invalid("动作配置超过大小上限 " + ruleProperties.getMaxPayloadBytes() + " 字节");
        }
        return json;
    }

    // ---------- 分支 / 循环结构（T-44） ----------

    /**
     * 结构校验：分支只允许向前跳转且不能从循环外跳入循环体；循环范围必须嵌套或互不相交；
     * 循环展开后的总执行次数受 {@link SceneConstants#MAX_EXECUTION_STEPS} 约束。
     */
    private void validateFlowStructure(List<SceneStep> steps, int totalSteps) {
        Set<Integer> enabledSeqs = new HashSet<>();
        List<LoopBlock> loops = new ArrayList<>();
        for (SceneStep step : steps) {
            if (step.getEnabled() != null && step.getEnabled() == 1) {
                enabledSeqs.add(step.getSeq());
            }
        }
        List<ParallelBlock> parallels = collectParallelBlocks(steps);
        for (SceneStep step : steps) {
            if (!SceneConstants.STEP_LOOP.equals(step.getActionType())
                    || !enabledSeqs.contains(step.getSeq())) {
                continue;
            }
            JsonNode config = readConfig(step.getActionConfig());
            Integer startSeq = intOrNull(config, "startSeq");
            Integer times = intOrNull(config, "times");
            if (startSeq == null || times == null) {
                continue;
            }
            if (startSeq >= step.getSeq()) {
                throw invalid("循环体起始序号必须小于循环步骤序号（第 " + step.getSeq() + " 步）");
            }
            if (!enabledSeqs.contains(startSeq)) {
                throw invalid("循环体起始步骤（第 " + startSeq + " 步）必须启用");
            }
            loops.add(new LoopBlock(startSeq, step.getSeq(), times));
        }
        for (int i = 0; i < loops.size(); i++) {
            for (int j = i + 1; j < loops.size(); j++) {
                LoopBlock first = loops.get(i);
                LoopBlock second = loops.get(j);
                boolean disjoint = first.loopSeq() < second.startSeq() || second.loopSeq() < first.startSeq();
                boolean firstContainsSecond =
                        first.startSeq() <= second.startSeq() && second.loopSeq() <= first.loopSeq();
                boolean secondContainsFirst =
                        second.startSeq() <= first.startSeq() && first.loopSeq() <= second.loopSeq();
                if (!disjoint && !firstContainsSecond && !secondContainsFirst) {
                    throw invalid("循环范围不能部分重叠：第 " + first.startSeq() + "–" + first.loopSeq()
                            + " 步与第 " + second.startSeq() + "–" + second.loopSeq() + " 步");
                }
            }
        }
        for (ParallelBlock parallel : parallels) {
            for (LoopBlock loop : loops) {
                boolean disjoint = parallel.joinSeq() < loop.startSeq() || loop.loopSeq() < parallel.startSeq();
                if (!disjoint) {
                    throw invalid("并行块（第 " + parallel.startSeq() + "–" + parallel.joinSeq()
                            + " 步）不能与循环体（第 " + loop.startSeq() + "–" + loop.loopSeq() + " 步）重叠");
                }
            }
        }

        for (SceneStep step : steps) {
            if (!SceneConstants.STEP_BRANCH.equals(step.getActionType())
                    || !enabledSeqs.contains(step.getSeq())) {
                continue;
            }
            JsonNode config = readConfig(step.getActionConfig());
            for (String field : List.of("trueSeq", "falseSeq")) {
                Integer target = intOrNull(config, field);
                if (target == null) {
                    continue;
                }
                if (target <= step.getSeq()) {
                    throw invalid("第 " + step.getSeq() + " 步的分支只能向前跳转（目标 " + target + "）");
                }
                if (target > totalSteps) {
                    throw invalid("第 " + step.getSeq() + " 步的分支目标超出步骤范围（目标 " + target + "）");
                }
                if (!enabledSeqs.contains(target)) {
                    throw invalid("第 " + step.getSeq() + " 步的分支目标（第 " + target + " 步）必须启用");
                }
                for (LoopBlock loop : loops) {
                    boolean targetInside = loop.startSeq() <= target && target <= loop.loopSeq();
                    boolean branchInside = loop.startSeq() <= step.getSeq() && step.getSeq() <= loop.loopSeq();
                    if (targetInside && !branchInside) {
                        throw invalid("第 " + step.getSeq() + " 步的分支不能从循环外跳入第 "
                                + loop.startSeq() + "–" + loop.loopSeq() + " 步的循环体");
                    }
                }
                for (ParallelBlock parallel : parallels) {
                    if (parallel.startSeq() <= target && target <= parallel.joinSeq()) {
                        throw invalid("第 " + step.getSeq() + " 步的分支不能跳入并行块（第 "
                                + parallel.startSeq() + "–" + parallel.joinSeq() + " 步）");
                    }
                }
            }
        }

        int totalExecutions = 0;
        for (SceneStep step : steps) {
            if (!enabledSeqs.contains(step.getSeq())) {
                continue;
            }
            long multiplier = 1;
            for (LoopBlock loop : loops) {
                if (loop.startSeq() <= step.getSeq() && step.getSeq() <= loop.loopSeq()) {
                    multiplier *= loop.times();
                    if (multiplier > SceneConstants.MAX_EXECUTION_STEPS) {
                        break;
                    }
                }
            }
            totalExecutions += (int) Math.min(multiplier, SceneConstants.MAX_EXECUTION_STEPS + 1L);
            if (totalExecutions > SceneConstants.MAX_EXECUTION_STEPS) {
                throw new BusinessException(ResultCode.SCENE_LIMIT_EXCEEDED,
                        "循环展开后的步骤执行次数不能超过 " + SceneConstants.MAX_EXECUTION_STEPS);
            }
        }
    }

    /**
     * 扫描并行块：PARALLEL 与 JOIN 必须成对、不能嵌套；块内只允许普通动作步骤，
     * 且每个启用步骤必须归属当前块的具体分支；每个分支至少一个启用步骤。
     */
    private List<ParallelBlock> collectParallelBlocks(List<SceneStep> steps) {
        List<ParallelBlock> blocks = new ArrayList<>();
        int activeStart = -1;
        int branches = 0;
        Map<Integer, Integer> enabledBranchCounts = new HashMap<>();
        for (SceneStep step : steps) {
            String type = step.getActionType();
            boolean enabled = step.getEnabled() == null || step.getEnabled() == 1;
            if (SceneConstants.STEP_PARALLEL.equals(type)) {
                if (!enabled) {
                    throw invalid("并行开始步骤（第 " + step.getSeq() + " 步）必须启用");
                }
                if (activeStart > 0) {
                    throw invalid("并行块不能嵌套：第 " + step.getSeq() + " 步位于第 " + activeStart + " 步的并行块内");
                }
                JsonNode config = readConfig(step.getActionConfig());
                Integer count = intOrNull(config, "branches");
                if (count == null || count < SceneConstants.MIN_PARALLEL_BRANCHES
                        || count > SceneConstants.MAX_PARALLEL_BRANCHES) {
                    throw invalid("并行分支数需在 [" + SceneConstants.MIN_PARALLEL_BRANCHES
                            + ", " + SceneConstants.MAX_PARALLEL_BRANCHES + "]");
                }
                activeStart = step.getSeq();
                branches = count;
                enabledBranchCounts.clear();
                continue;
            }
            if (SceneConstants.STEP_JOIN.equals(type)) {
                if (!enabled) {
                    throw invalid("并行汇合步骤（第 " + step.getSeq() + " 步）必须启用");
                }
                if (activeStart < 0) {
                    throw invalid("第 " + step.getSeq() + " 步的并行汇合没有对应的并行开始");
                }
                for (int branch = 0; branch < branches; branch++) {
                    if (enabledBranchCounts.getOrDefault(branch, 0) == 0) {
                        throw invalid("并行块（第 " + activeStart + " 步）的分支 " + (branch + 1)
                                + " 至少需要一个启用步骤");
                    }
                }
                blocks.add(new ParallelBlock(activeStart, step.getSeq(), branches));
                activeStart = -1;
                branches = 0;
                continue;
            }
            if (activeStart > 0) {
                if (SceneConstants.FLOW_STEPS.contains(type)) {
                    throw invalid("并行块内只允许普通动作步骤（第 " + step.getSeq() + " 步为 " + type + "）");
                }
                Integer parallelSeq = step.getParallelSeq();
                Integer branchIndex = step.getBranchIndex();
                if (parallelSeq == null || parallelSeq != activeStart) {
                    throw invalid("第 " + step.getSeq() + " 步必须归属并行块（parallelSeq=" + activeStart + "）");
                }
                if (branchIndex == null || branchIndex < 0 || branchIndex >= branches) {
                    throw invalid("第 " + step.getSeq() + " 步的 branchIndex 需在 [0, " + (branches - 1) + "]");
                }
                if (enabled) {
                    enabledBranchCounts.merge(branchIndex, 1, Integer::sum);
                }
            } else if (step.getParallelSeq() != null || step.getBranchIndex() != null) {
                throw invalid("第 " + step.getSeq() + " 步不在并行块内，不能设置 parallelSeq / branchIndex");
            }
        }
        if (activeStart > 0) {
            throw invalid("并行开始（第 " + activeStart + " 步）缺少对应的并行汇合步骤");
        }
        return blocks;
    }

    private record LoopBlock(int startSeq, int loopSeq, int times) {
    }

    private record ParallelBlock(int startSeq, int joinSeq, int branches) {
    }

    private JsonNode readConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    private void checkPayloadTemplate(JsonNode config) {
        String template = text(config, "payloadTemplate");
        if (template != null && template.getBytes(StandardCharsets.UTF_8).length > ruleProperties.getMaxPayloadBytes()) {
            throw invalid("载荷模板超过大小上限 " + ruleProperties.getMaxPayloadBytes() + " 字节");
        }
    }

    // ---------- 工具 ----------

    private void checkDeviceOwned(Long userId, Long deviceId) {
        Device device = deviceService.getDeviceById(deviceId);
        if (!userId.equals(device.getOwnerId())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
    }

    private static void checkIdentifier(String identifier, String actionKind) {
        if (identifier == null) {
            throw invalid(actionKind + "必须填写标识符");
        }
        if (identifier.length() > RuleConstants.MAX_IDENTIFIER_LENGTH) {
            throw invalid("标识符长度不能超过 " + RuleConstants.MAX_IDENTIFIER_LENGTH);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static boolean isDecimal(String value) {
        try {
            new BigDecimal(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw invalid(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.SCENE_INVALID, message);
    }
}