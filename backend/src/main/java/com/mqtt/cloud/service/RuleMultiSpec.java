package com.mqtt.cloud.service;

import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.entity.RuleDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 规则多条件 / 多动作的解析模型（T-33 / G-6-7）。
 * <p>
 * 存在的唯一理由是**回落**：{@code conditions_json} / {@code actions_json} 为空时，
 * 一律回落到 V9 的单条件单动作列（{@code identifier}/{@code operator}/{@code threshold_value} 与
 * {@code action_type}/{@code action_config}），使存量规则的求值与执行路径逐位不变。
 * <p>
 * 解析失败（JSON 非法 / 结构不符）一律返回 {@code null} 表示「回落单条件单动作」，
 * 而不是抛异常中断整条规则的评估 —— 一条规则的配置问题不应导致同一用户所有规则停摆。
 * 服务层的写入校验（{@code RuleServiceImpl}）会把非法配置挡在入库之前，这里只是兜底。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public final class RuleMultiSpec {

    private RuleMultiSpec() {
    }

    /**
     * 单个触发条件。
     *
     * @param identifier     属性标识符或事件标识符
     * @param operator       比较符；事件条件为 {@code null}
     * @param thresholdValue 比较阈值；事件条件为 {@code null}
     * @param eventType      事件类型过滤；属性条件为 {@code null}
     */
    public record Condition(String identifier, String operator, String thresholdValue, String eventType) {
    }

    /**
     * 单个动作。
     *
     * @param actionType   动作类型（{@link RuleConstants#ACTIONS} 之一）
     * @param actionConfig 动作配置对象
     */
    public record Action(String actionType, JsonNode actionConfig) {
    }

    /**
     * 取规则的条件列表：优先多条件 JSON，为空则回落单条件列。
     *
     * @param rule   规则定义
     * @param mapper JSON 解析器（由调用方注入，与项目其余服务一致）
     * @return 条件列表，永不为空；单条件规则返回只含一项的列表
     */
    public static List<Condition> conditionsOf(RuleDefinition rule, ObjectMapper mapper) {
        List<Condition> parsed = parseArray(rule.getConditionsJson(), mapper, Condition.class, node ->
                new Condition(text(node, "identifier"), text(node, "operator"),
                        text(node, "thresholdValue"), text(node, "eventType")));
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
        // 回落：单条件规则用旧列构造，条件字段语义与 V9 完全一致
        return List.of(new Condition(rule.getIdentifier(), rule.getOperator(),
                rule.getThresholdValue(), rule.getEventType()));
    }

    /**
     * 取规则的动作列表：优先多动作 JSON，为空则回落单动作列。
     *
     * @return 动作列表，永不为空；单动作规则返回只含一项的列表
     */
    public static List<Action> actionsOf(RuleDefinition rule, ObjectMapper mapper) {
        List<Action> parsed = parseArray(rule.getActionsJson(), mapper, Action.class, node -> {
            JsonNode config = node.get("actionConfig");
            return new Action(text(node, "actionType"), config != null && config.isObject() ? config : null);
        });
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
        JsonNode config = null;
        String raw = rule.getActionConfig();
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode node = mapper.readTree(raw);
                config = node != null && node.isObject() ? node : null;
            } catch (Exception e) {
                config = null;
            }
        }
        return List.of(new Action(rule.getActionType(), config));
    }

    /**
     * 规则是否为多条件形态（用于选择求值分支）。
     * <p>
     * 判定只看 {@code conditionsJson} 是否**解析出非空数组**，与 {@link #conditionsOf} 的回落条件保持一致，
     * 避免出现「判为多条件但求值时又回落单条件」的分叉。
     */
    public static boolean hasMultipleConditions(RuleDefinition rule, ObjectMapper mapper) {
        List<Condition> parsed = parseArray(rule.getConditionsJson(), mapper, Condition.class, node ->
                new Condition(text(node, "identifier"), text(node, "operator"),
                        text(node, "thresholdValue"), text(node, "eventType")));
        return parsed != null && !parsed.isEmpty();
    }

    /** 规则是否为多动作形态（决定一次触发落一条还是多条执行记录）。 */
    public static boolean hasMultipleActions(RuleDefinition rule, ObjectMapper mapper) {
        List<Action> parsed = parseArray(rule.getActionsJson(), mapper, Action.class, node -> {
            JsonNode config = node.get("actionConfig");
            return new Action(text(node, "actionType"), config != null && config.isObject() ? config : null);
        });
        return parsed != null && !parsed.isEmpty();
    }

    /** 条件组合逻辑；缺省或非法一律按 {@code AND}（与「全部满足」的单条件直觉一致）。 */
    public static String logicOf(RuleDefinition rule) {
        String logic = rule.getConditionLogic();
        return logic != null && RuleConstants.LOGICS.contains(logic.trim().toUpperCase())
                ? logic.trim().toUpperCase() : RuleConstants.LOGIC_AND;
    }

    /**
     * 通用数组解析：返回 {@code null} 表示「不是数组 / 解析失败 / 元素结构不符，应回落」。
     *
     * @param raw      JSON 数组文本
     * @param mapper   JSON 解析器
     * @param ignored  仅用于类型擦除，保证调用点语义清晰
     * @param factory  元素工厂
     */
    private static <T> List<T> parseArray(String raw, ObjectMapper mapper, Class<T> ignored,
                                          java.util.function.Function<JsonNode, T> factory) {
        if (raw == null || raw.isBlank() || mapper == null) {
            return null;
        }
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            return null;
        }
        List<T> result = new java.util.ArrayList<>(root.size());
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                // 元素结构不符：整体回落，避免「半条规则生效」的中间态
                return null;
            }
            T value = factory.apply(item);
            if (value == null) {
                return null;
            }
            result.add(value);
        }
        return result;
    }

    /** 取非空文本并去首尾空白；非文本或全空白返回 {@code null}。 */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}