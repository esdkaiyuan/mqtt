package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.dto.request.RuleQueryDTO;
import com.mqtt.cloud.dto.request.RuleRequest;
import com.mqtt.cloud.dto.request.RuleTestRequest;
import com.mqtt.cloud.dto.response.RuleTestResult;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.RuleDefinition;
import com.mqtt.cloud.mapper.RuleDefinitionMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceScopeService;
import com.mqtt.cloud.service.RuleEvaluationService;
import com.mqtt.cloud.service.RuleMultiSpec;
import com.mqtt.cloud.service.RuleService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.service.scope.RuleScope;
import com.mqtt.cloud.util.ScopeJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 消息规则服务实现（T-19 设计文档 §7 / §10.1）。
 * <p>
 * 校验集中在本类：非法配置统一抛 {@code 6219}，非法动作类型抛 {@code 6220}，作用域非法抛 {@code 6269}，
 * 越权设备抛 {@code 2003}，规则数超上限抛 {@code 6222}。设备归属复用 {@link DeviceService#getDeviceById(Long)}
 * （不存在抛 {@code 2002}，非本人抛 {@code 2003}）；分组 / 标签归属与作用域数量上限交由
 * {@link DeviceScopeService#validateAndNormalize}（越权 {@code 403}）。写入后主动失效本副本规则缓存。
 * <p>
 * {@code actionConfig} 在请求 / 响应中为对象，本类负责与库中 {@code TEXT} 的序列化 / 反序列化；
 * 序列化失败按 {@code 6219} 拒绝，不落库。校验**不查询物模型**（保存时设备可能未定），
 * 标识符存在性留待执行时校验与试运行诊断。
 */
@Slf4j
@Service
public class RuleServiceImpl extends ServiceImpl<RuleDefinitionMapper, RuleDefinition> implements RuleService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DeviceService deviceService;
    private final DeviceScopeService deviceScopeService;
    private final ThingModelService thingModelService;
    private final RuleEvaluationService ruleEvaluationService;
    private final RuleProperties properties;
    private final ObjectMapper objectMapper;
    private final ScopeJson scopeJson;

    public RuleServiceImpl(DeviceService deviceService,
                           DeviceScopeService deviceScopeService,
                           ThingModelService thingModelService,
                           RuleEvaluationService ruleEvaluationService,
                           RuleProperties properties,
                           ObjectMapper objectMapper,
                           ScopeJson scopeJson) {
        this.deviceService = deviceService;
        this.deviceScopeService = deviceScopeService;
        this.thingModelService = thingModelService;
        this.ruleEvaluationService = ruleEvaluationService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.scopeJson = scopeJson;
    }

    @Override
    public IPage<RuleDefinition> page(Long userId, RuleQueryDTO query) {
        RuleQueryDTO effective = query == null ? new RuleQueryDTO() : query;
        int pageNum = (effective.getPageNum() == null || effective.getPageNum() < 1)
                ? 1 : effective.getPageNum();
        int pageSize = (effective.getPageSize() == null || effective.getPageSize() < 1)
                ? DEFAULT_PAGE_SIZE : Math.min(effective.getPageSize(), MAX_PAGE_SIZE);

        IPage<RuleDefinition> result = baseMapper.pageByUser(new Page<>(pageNum, pageSize), userId,
                normalizeUpper(effective.getSourceType()), normalizeUpper(effective.getActionType()),
                effective.getEnabled(), trimToNull(effective.getKeyword()));
        result.getRecords().forEach(this::populateView);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleDefinition create(Long userId, RuleRequest request) {
        if (baseMapper.countByUser(userId) >= properties.getMaxRulesPerUser()) {
            throw new BusinessException(ResultCode.RULE_LIMIT_EXCEEDED,
                    "规则数量已达上限 " + properties.getMaxRulesPerUser());
        }
        RuleDefinition rule = new RuleDefinition();
        rule.setUserId(userId);
        applyAndValidate(rule, request, userId, null);
        save(rule);
        ruleEvaluationService.evictCache(userId);
        populateView(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleDefinition update(Long userId, Long id, RuleRequest request) {
        RuleDefinition rule = getOwned(userId, id);
        applyAndValidate(rule, request, userId, id);
        updateById(rule);
        ruleEvaluationService.evictCache(userId);
        populateView(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        RuleDefinition rule = getOwned(userId, id);
        removeById(rule.getId());
        ruleEvaluationService.evictCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long userId, Long id, boolean enabled) {
        RuleDefinition rule = getOwned(userId, id);
        rule.setEnabled(enabled ? 1 : 0);
        updateById(rule);
        ruleEvaluationService.evictCache(userId);
    }

    @Override
    public RuleDefinition getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.RULE_NOT_FOUND);
        }
        RuleDefinition rule = getById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.RULE_NOT_FOUND);
        }
        if (!userId.equals(rule.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        populateView(rule);
        return rule;
    }

    @Override
    public void evictCache(Long userId) {
        ruleEvaluationService.evictCache(userId);
    }

    @Override
    public RuleTestResult test(Long userId, Long id, RuleTestRequest request) {
        RuleDefinition rule = getOwned(userId, id);
        if (request == null || request.getDeviceId() == null) {
            throw invalid("试运行必须指定设备");
        }
        String identifier = trimToNull(request.getIdentifier());
        if (identifier == null || identifier.length() > RuleConstants.MAX_IDENTIFIER_LENGTH) {
            throw invalid("试运行标识符不能为空且长度不超过 " + RuleConstants.MAX_IDENTIFIER_LENGTH);
        }
        String valueText = request.getValueText();
        if (RuleConstants.SOURCE_PROPERTY.equals(rule.getSourceType())
                && RuleConstants.NUMERIC_OPERATORS.contains(rule.getOperator())
                && valueText != null && !valueText.isBlank() && !isDecimal(valueText)) {
            throw invalid("数值比较符下取值必须是可解析的十进制数");
        }

        Device device = deviceService.getDeviceById(request.getDeviceId());
        if (!userId.equals(device.getOwnerId())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
        ThingModelDefinition definition = thingModelService.getForProduct(device.getProductId());
        boolean deviceModeled = !definition.isEmpty();

        // T-33：多条件规则的试运行只提供「一个标识符 + 一个取值」，只能判定与该标识符相关的那一个条件，
        // 其余条件因缺值在 AND 下必然不满足。故这里刻意**不声称**「已完整判定」，
        // 而是把监听的条件总数与实际参与判定的条件一并写进 reason，避免用户误读为「规则能命中」。
        boolean multi = RuleMultiSpec.hasMultipleConditions(rule, objectMapper);
        int conditionCount = RuleMultiSpec.conditionsOf(rule, objectMapper).size();
        String multiNote = multi
                ? "（该规则共 " + conditionCount + " 个条件，试运行只提供了 1 个属性取值，"
                + "其余条件按缺值处理；完整判定需设备实际上报全部相关属性）"
                : "";

        if (RuleConstants.SOURCE_EVENT.equals(rule.getSourceType())) {
            boolean identifierModeled = definition.events().containsKey(rule.getIdentifier());
            boolean matched = rule.getIdentifier() != null && rule.getIdentifier().equals(identifier);
            String reason = (matched
                    ? "事件标识符匹配" + (rule.getEventType() == null ? "" : "（事件类型过滤 " + rule.getEventType() + " 需实际上报核对）")
                    : "标识符不匹配（规则监听 " + rule.getIdentifier() + "）") + multiNote;
            ActionDiagnosis diagnosis = describeAction(rule, definition);
            return new RuleTestResult(matched, reason, deviceModeled, identifierModeled,
                    diagnosis.executable(), diagnosis.summary() + actionMultiNote(rule));
        }

        boolean identifierModeled = definition.properties().containsKey(rule.getIdentifier());
        boolean matched;
        String reason;
        if (!rule.getIdentifier().equals(identifier)) {
            matched = false;
            reason = "标识符不匹配（规则监听 " + rule.getIdentifier() + "）" + multiNote;
        } else {
            Boolean result = RuleConditionMatcher.matchesProperty(valueText, rule.getOperator(), rule.getThresholdValue());
            if (result == null) {
                matched = false;
                reason = "取值或阈值不可解析为数值" + multiNote;
            } else if (result) {
                matched = !multi;
                reason = (valueText + " " + operatorLabel(rule.getOperator()) + " " + rule.getThresholdValue())
                        + (multi ? "（仅该条件满足，其余条件缺值 → 按 AND 判不命中）" : "");
            } else {
                matched = false;
                reason = "条件不满足：" + valueText + " 未满足 " + operatorLabel(rule.getOperator())
                        + " " + rule.getThresholdValue() + multiNote;
            }
        }
        ActionDiagnosis diagnosis = describeAction(rule, definition);
        return new RuleTestResult(matched, reason, deviceModeled, identifierModeled,
                diagnosis.executable(), diagnosis.summary() + actionMultiNote(rule));
    }

    /** 多动作规则的试运行说明：动作诊断只覆盖首个动作，必须显式提示其余动作未被检查。 */
    private String actionMultiNote(RuleDefinition rule) {
        int count = RuleMultiSpec.actionsOf(rule, objectMapper).size();
        return count > 1 ? "；该规则共 " + count + " 个动作，试运行只诊断了第一个" : "";
    }

    /**
     * 校验请求并把规范化后的字段写入 {@code rule}。任何非法项抛 {@code 6219} / {@code 6220}。
     *
     * @param excludeId 更新时排除自身用于重名校验，创建时为 {@code null}
     */
    private void applyAndValidate(RuleDefinition rule, RuleRequest request, Long userId, Long excludeId) {
        if (request == null) {
            throw invalid("请求体不能为空");
        }

        String name = trimToNull(request.getName());
        if (name == null) {
            throw invalid("规则名称不能为空");
        }
        if (name.length() > RuleConstants.MAX_NAME_LENGTH) {
            throw invalid("规则名称长度不能超过 " + RuleConstants.MAX_NAME_LENGTH);
        }
        if (baseMapper.countByUserAndName(userId, name, excludeId) > 0) {
            throw invalid("规则名称已存在");
        }

        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > RuleConstants.MAX_DESCRIPTION_LENGTH) {
            throw invalid("描述长度不能超过 " + RuleConstants.MAX_DESCRIPTION_LENGTH);
        }

        String sourceType = normalizeUpper(request.getSourceType());
        if (sourceType == null || !RuleConstants.SOURCES.contains(sourceType)) {
            throw invalid("触发源必须是 PROPERTY / EVENT");
        }

        String actionType = normalizeUpper(request.getActionType());
        if (actionType == null) {
            throw new BusinessException(ResultCode.RULE_ACTION_UNSUPPORTED, "动作类型不能为空");
        }
        if (!RuleConstants.ACTIONS.contains(actionType)) {
            throw new BusinessException(ResultCode.RULE_ACTION_UNSUPPORTED, "不支持的动作类型 " + actionType);
        }

        RuleScope scope = deviceScopeService.validateAndNormalize(userId,
                RuleScope.of(request.getScopeType(), request.getDeviceId(),
                        request.getGroupIds(), request.getTagIds()),
                ResultCode.RULE_SCOPE_INVALID);
        Long deviceId = scope.deviceId();
        if (RuleConstants.SCOPE_DEVICE.equals(scope.scopeType())) {
            Device device = deviceService.getDeviceById(deviceId);
            if (!userId.equals(device.getOwnerId())) {
                throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
            }
        }

        int cooldownSeconds = request.getCooldownSeconds() == null ? 0 : request.getCooldownSeconds();
        if (cooldownSeconds < 0 || cooldownSeconds > RuleConstants.MAX_COOLDOWN_SECONDS) {
            throw invalid("冷却窗口取值需在 [0, " + RuleConstants.MAX_COOLDOWN_SECONDS + "] 秒");
        }

        int enabled = request.getEnabled() == null ? 1 : (request.getEnabled() ? 1 : 0);

        String identifier = trimToNull(request.getIdentifier());
        String operator = normalizeUpper(request.getOperator());
        String thresholdValue = trimToNull(request.getThresholdValue());
        String eventType = trimToNull(request.getEventType());
        if (eventType != null) {
            eventType = eventType.toLowerCase();
        }

        if (RuleConstants.SOURCE_PROPERTY.equals(sourceType)) {
            checkIdentifier(identifier, "属性规则");
            if (operator == null || !RuleConstants.OPERATORS.contains(operator)) {
                throw invalid("比较符必须是 GT / GTE / LT / LTE / EQ / NE");
            }
            if (thresholdValue == null) {
                throw invalid("属性规则必须填写阈值");
            }
            if (thresholdValue.length() > RuleConstants.MAX_THRESHOLD_LENGTH) {
                throw invalid("阈值长度不能超过 " + RuleConstants.MAX_THRESHOLD_LENGTH);
            }
            if (RuleConstants.NUMERIC_OPERATORS.contains(operator) && !isDecimal(thresholdValue)) {
                throw invalid("数值比较符下阈值必须是可解析的十进制数");
            }
            eventType = null;
        } else {
            checkIdentifier(identifier, "事件规则");
            if (operator != null || thresholdValue != null) {
                throw invalid("事件规则不支持比较符与阈值");
            }
            if (eventType != null && eventType.length() > RuleConstants.MAX_EVENT_TYPE_LENGTH) {
                throw invalid("事件类型长度不能超过 " + RuleConstants.MAX_EVENT_TYPE_LENGTH);
            }
            operator = null;
            thresholdValue = null;
        }

        String actionConfigJson = validateAndSerializeAction(actionType, request.getActionConfig());
        rule.setName(name);
        rule.setDescription(description);
        rule.setScopeType(scope.scopeType());
        rule.setDeviceId(deviceId);
        rule.setGroupIds(scopeJson.write(scope.groupIds()));
        rule.setTagIds(scopeJson.write(scope.tagIds()));
        rule.setSourceType(sourceType);
        rule.setIdentifier(identifier);
        rule.setOperator(operator);
        rule.setThresholdValue(thresholdValue);
        rule.setEventType(eventType);
        rule.setActionType(actionType);
        rule.setActionConfig(actionConfigJson);

        // ---- T-33：多条件 / 多动作（放在单值赋值之后，否则会被下面的 set 覆盖）----
        // 策略是「多值优先、单值回填旧列」：数组非空时以数组为准，同时把首个元素回填到旧列，
        // 使任何仍读 identifier / action_type 的存量路径（列表筛选、导出、旧版前端）继续拿到合理值。
        MultiRule multi = resolveMulti(request, sourceType);
        if (multi.conditionsJson() != null) {
            rule.setConditionLogic(multi.logic());
            rule.setConditionsJson(multi.conditionsJson());
            rule.setIdentifier(multi.firstIdentifier());
            rule.setOperator(multi.firstOperator());
            rule.setThresholdValue(multi.firstThreshold());
            rule.setEventType(multi.firstEventType());
        } else {
            rule.setConditionLogic(RuleConstants.LOGIC_AND);
            rule.setConditionsJson(null);
        }
        if (multi.actionsJson() != null) {
            rule.setActionsJson(multi.actionsJson());
            rule.setActionType(multi.firstActionType());
            rule.setActionConfig(multi.firstActionConfigJson());
        } else {
            rule.setActionsJson(null);
        }

        rule.setCooldownSeconds(cooldownSeconds);
        rule.setEnabled(enabled);
    }

    /**
     * 多条件 / 多动作解析结果（T-33）。
     *
     * @param conditionsJson        多条件 JSON 文本；{@code null} 表示走单条件路径
     * @param logic                 组合逻辑
     * @param actionsJson           多动作 JSON 文本；{@code null} 表示走单动作路径
     * @param firstIdentifier       首个条件标识符（回填旧列）
     * @param firstOperator         首个条件比较符（回填旧列）
     * @param firstThreshold        首个条件阈值（回填旧列）
     * @param firstEventType        首个条件事件类型（回填旧列）
     * @param firstActionType       首个动作类型（回填旧列）
     * @param firstActionConfigJson 首个动作配置 JSON（回填旧列）
     */
    private record MultiRule(String conditionsJson, String logic, String actionsJson,
                             String firstIdentifier, String firstOperator, String firstThreshold,
                             String firstEventType, String firstActionType, String firstActionConfigJson) {
    }

    /**
     * 校验并序列化多条件 / 多动作（T-33）。
     * <p>
     * 数组为空（或全 null 元素）时返回 {@code null} 两项，调用方据此走单条件 / 单动作路径 ——
     * 与改造前完全一致。
     * <p>
     * 超限时**显式拒绝而非静默截断**：悄悄丢掉多余条件等价于悄悄改了规则语义，
     * 用户会在「规则生效但少了一个条件」里反复排查。
     *
     * @param sourceType 触发源，决定条件是属性型（带比较符）还是事件型（只带事件类型）
     */
    private MultiRule resolveMulti(RuleRequest request, String sourceType) {
        return new MultiRule(
                resolveConditions(request, sourceType),
                resolveLogic(request.getConditionLogic()),
                resolveActions(request),
                null, null, null, null, null, null);
    }

    /** 组合逻辑归一化：缺省或非法一律按 {@code AND}，与单条件的直觉一致。 */
    private String resolveLogic(String raw) {
        String logic = normalizeUpper(raw);
        return logic != null && RuleConstants.LOGICS.contains(logic) ? logic : RuleConstants.LOGIC_AND;
    }

    /** 多条件校验与序列化；返回 {@code null} 表示无多条件。 */
    private String resolveConditions(RuleRequest request, String sourceType) {
        List<JsonNode> conditions = request.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        if (conditions.size() > RuleConstants.MAX_CONDITIONS) {
            throw invalid("条件数不能超过 " + RuleConstants.MAX_CONDITIONS);
        }
        boolean property = RuleConstants.SOURCE_PROPERTY.equals(sourceType);
        StringBuilder json = new StringBuilder("[");
        for (JsonNode node : conditions) {
            if (node == null || !node.isObject()) {
                throw invalid("条件必须是对象");
            }
            String identifier = text(node, "identifier");
            if (identifier == null) {
                throw invalid("条件必须填写标识符");
            }
            checkIdentifier(identifier, "条件");
            String operator = null;
            String thresholdValue = null;
            String eventType = null;
            if (property) {
                operator = normalizeUpper(text(node, "operator"));
                if (operator == null || !RuleConstants.OPERATORS.contains(operator)) {
                    throw invalid("条件的比较符必须是 " + String.join(" / ", RuleConstants.OPERATORS));
                }
                thresholdValue = text(node, "thresholdValue");
                if (thresholdValue == null) {
                    throw invalid("条件必须填写阈值");
                }
                if (thresholdValue.length() > RuleConstants.MAX_THRESHOLD_LENGTH) {
                    throw invalid("阈值长度不能超过 " + RuleConstants.MAX_THRESHOLD_LENGTH);
                }
                if (RuleConstants.NUMERIC_OPERATORS.contains(operator)) {
                    try {
                        new BigDecimal(thresholdValue.trim());
                    } catch (NumberFormatException e) {
                        throw invalid("数值比较符的阈值必须是可解析的十进制数");
                    }
                }
            } else {
                eventType = text(node, "eventType");
                if (eventType != null && eventType.length() > RuleConstants.MAX_EVENT_TYPE_LENGTH) {
                    throw invalid("事件类型长度不能超过 " + RuleConstants.MAX_EVENT_TYPE_LENGTH);
                }
            }
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"identifier\":").append(quote(identifier));
            if (operator != null) {
                json.append(",\"operator\":").append(quote(operator));
            }
            if (thresholdValue != null) {
                json.append(",\"thresholdValue\":").append(quote(thresholdValue));
            }
            if (eventType != null) {
                json.append(",\"eventType\":").append(quote(eventType));
            }
            json.append('}');
        }
        return json.append(']').toString();
    }

    /** 多动作校验与序列化；返回 {@code null} 表示无多动作。 */
    private String resolveActions(RuleRequest request) {
        List<JsonNode> actions = request.getActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        if (actions.size() > RuleConstants.MAX_ACTIONS) {
            throw invalid("动作数不能超过 " + RuleConstants.MAX_ACTIONS);
        }
        StringBuilder json = new StringBuilder("[");
        for (JsonNode node : actions) {
            if (node == null || !node.isObject()) {
                throw invalid("动作必须是对象");
            }
            String actionType = normalizeUpper(text(node, "actionType"));
            if (actionType == null) {
                throw new BusinessException(ResultCode.RULE_ACTION_UNSUPPORTED, "动作类型不能为空");
            }
            if (!RuleConstants.ACTIONS.contains(actionType)) {
                throw new BusinessException(ResultCode.RULE_ACTION_UNSUPPORTED,
                        "不支持的动作类型 " + actionType);
            }
            JsonNode config = node.get("actionConfig");
            // 每个动作单独走一次既有校验，保证多动作与单动作的配置约束完全一致
            String configJson = validateAndSerializeAction(actionType, config);
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"actionType\":").append(quote(actionType))
                    .append(",\"actionConfig\":").append(configJson).append('}');
        }
        return json.append(']').toString();
    }

    /** JSON 字符串字面量转义（值均由前端提供，直接拼接需转义引号与控制字符）。 */
    private String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** 按动作类型校验 {@code action_config}，返回落库用 JSON 文本。 */
    private String validateAndSerializeAction(String actionType, JsonNode config) {
        if (config == null || !config.isObject()) {
            throw invalid("动作配置不能为空");
        }
        switch (actionType) {
            case RuleConstants.ACTION_UPDATE_PROPERTY -> {
                checkIdentifier(text(config, "identifier"), "更新属性动作");
                String value = text(config, "value");
                if (value == null) {
                    throw invalid("更新属性动作必须填写取值");
                }
                if (value.length() > 512) {
                    throw invalid("更新属性动作取值长度不能超过 512");
                }
            }
            case RuleConstants.ACTION_SEND_COMMAND -> {
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
            case RuleConstants.ACTION_FORWARD_MQTT -> {
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
            case RuleConstants.ACTION_FORWARD_HTTP -> {
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
            default -> throw new BusinessException(ResultCode.RULE_ACTION_UNSUPPORTED, "不支持的动作类型 " + actionType);
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw invalid("动作配置序列化失败");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
            throw invalid("动作配置超过大小上限 " + properties.getMaxPayloadBytes() + " 字节");
        }
        return json;
    }

    private void checkPayloadTemplate(JsonNode config) {
        String template = text(config, "payloadTemplate");
        if (template != null && template.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
            throw invalid("载荷模板超过大小上限 " + properties.getMaxPayloadBytes() + " 字节");
        }
    }

    /** 试运行动作可执行性诊断（不产生副作用，仅依据物模型与开关静态判断）。 */
    private ActionDiagnosis describeAction(RuleDefinition rule, ThingModelDefinition definition) {
        JsonNode config = readActionConfig(rule);
        if (config == null) {
            return new ActionDiagnosis(false, "动作配置缺失");
        }
        return switch (rule.getActionType()) {
            case RuleConstants.ACTION_UPDATE_PROPERTY -> {
                String target = text(config, "identifier");
                ThingModelDefinition.PropertySpec spec = definition.properties().get(target);
                if (spec == null) {
                    yield new ActionDiagnosis(false, "目标属性 " + target + " 未在物模型中定义");
                }
                if (!spec.writable()) {
                    yield new ActionDiagnosis(false, "目标属性 " + target + " 不可写（accessMode 非 rw）");
                }
                yield new ActionDiagnosis(true, "写入云端属性 " + target + " = " + text(config, "value"));
            }
            case RuleConstants.ACTION_SEND_COMMAND -> {
                String commandType = text(config, "commandType");
                String target = text(config, "identifier");
                if ("property_set".equals(commandType)) {
                    ThingModelDefinition.PropertySpec spec = definition.properties().get(target);
                    if (spec == null) {
                        yield new ActionDiagnosis(false, "目标属性 " + target + " 未在物模型中定义");
                    }
                    if (!spec.writable()) {
                        yield new ActionDiagnosis(false, "目标属性 " + target + " 不可写");
                    }
                } else if (!definition.services().containsKey(target)) {
                    yield new ActionDiagnosis(false, "服务 " + target + " 未在物模型中定义");
                }
                yield new ActionDiagnosis(true, "下发命令 " + commandType + " → " + target);
            }
            case RuleConstants.ACTION_FORWARD_MQTT -> {
                if (!properties.getMqtt().isEnabled()) {
                    yield new ActionDiagnosis(false, "外部 MQTT 转发未启用（app.rule.mqtt.enabled=false）");
                }
                if (properties.getMqtt().getBrokerUrl() == null || properties.getMqtt().getBrokerUrl().isBlank()) {
                    yield new ActionDiagnosis(false, "外部 MQTT 转发未配置 Broker 地址");
                }
                yield new ActionDiagnosis(true, "转发 MQTT → " + text(config, "topic"));
            }
            case RuleConstants.ACTION_FORWARD_HTTP -> {
                String method = text(config, "method");
                yield new ActionDiagnosis(true,
                        (method == null ? "POST" : method.toUpperCase()) + " " + text(config, "url"));
            }
            default -> new ActionDiagnosis(false, "不支持的动作类型");
        };
    }

    /**
     * 装配返回前端的瞬态视图：动作配置对象、作用域 ID 数组与多条件 / 多动作数组（T-33）；解析失败按空值降级。
     * <p>
     * 单条件 / 单动作规则也会被装配成**长度为一**的 {@code conditions} / {@code actions} 数组，
     * 前端因此只需一套渲染逻辑；解析失败或列为空时返回空列表，由前端回落到单条件表单。
     */
    private void populateView(RuleDefinition rule) {
        rule.setActionConfigView(readActionConfig(rule));
        rule.setGroupIdsView(scopeJson.toIdList(rule.getGroupIds()));
        rule.setTagIdsView(scopeJson.toIdList(rule.getTagIds()));
        rule.setConditionsView(readArray(rule.getConditionsJson()));
        rule.setActionsView(readArray(rule.getActionsJson()));
    }

    /** 解析多条件 / 多动作 JSON 数组；非法或为空时返回空列表（前端据此回落单值表单）。 */
    private List<Object> readArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<List<Object>>() {
            });
        } catch (Exception e) {
            log.warn("规则多条件 / 多动作解析失败，按单值展示: json={}", abbreviate(json), e);
            return List.of();
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 120 ? value : value.substring(0, 120) + "...";
    }

    private JsonNode readActionConfig(RuleDefinition rule) {
        String json = rule.getActionConfig();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("规则动作配置解析失败: ruleId={}", rule.getId(), e);
            return null;
        }
    }

    private static void checkIdentifier(String identifier, String ruleKind) {
        if (identifier == null) {
            throw invalid(ruleKind + "必须填写标识符");
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
        return new BusinessException(ResultCode.RULE_INVALID, message);
    }

    private static String operatorLabel(String operator) {
        if (operator == null) {
            return "满足";
        }
        return switch (operator) {
            case RuleConstants.OPERATOR_GT -> "超过";
            case RuleConstants.OPERATOR_GTE -> "不低于";
            case RuleConstants.OPERATOR_LT -> "低于";
            case RuleConstants.OPERATOR_LTE -> "不高于";
            case RuleConstants.OPERATOR_EQ -> "等于";
            case RuleConstants.OPERATOR_NE -> "不等于";
            default -> "满足";
        };
    }

    /** 动作可执行性诊断结果。 */
    private record ActionDiagnosis(boolean executable, String summary) {
    }
}