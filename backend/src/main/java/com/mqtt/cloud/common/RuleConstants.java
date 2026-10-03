package com.mqtt.cloud.common;

import java.util.Set;

/**
 * 消息规则 / 规则引擎共享常量（T-19 设计文档 §3 / §7 / §10.3）。
 * <p>
 * 集中触发源 / 动作 / 状态 / 比较符 / 事件类型 / Webhook 事件名 / 占位符的字符串口径，
 * 避免评估、服务、动作执行、模板渲染各处散落字面量而产生漂移。
 * <p>
 * 比较符取值与 T-17 告警**语义一致**（数值按 {@code BigDecimal.compareTo}、{@code EQ}/{@code NE} 按归一化文本），
 * 但在此独立定义而非引用 {@link AlertConstants}，以保持规则域与告警域解耦。
 */
public final class RuleConstants {

    private RuleConstants() {
    }

    // ---------- 触发源 ----------

    /** 触发源：属性上报。 */
    public static final String SOURCE_PROPERTY = "PROPERTY";
    /** 触发源：事件上报。 */
    public static final String SOURCE_EVENT = "EVENT";

    /** 全部触发源类型。 */
    public static final Set<String> SOURCES = Set.of(SOURCE_PROPERTY, SOURCE_EVENT);

    // ---------- 动作类型 ----------

    /** 动作：更新云端属性。 */
    public static final String ACTION_UPDATE_PROPERTY = "UPDATE_PROPERTY";
    /** 动作：下发命令。 */
    public static final String ACTION_SEND_COMMAND = "SEND_COMMAND";
    /** 动作：转发外部 MQTT。 */
    public static final String ACTION_FORWARD_MQTT = "FORWARD_MQTT";
    /** 动作：转发 HTTP。 */
    public static final String ACTION_FORWARD_HTTP = "FORWARD_HTTP";

    /** 全部动作类型。 */
    public static final Set<String> ACTIONS = Set.of(
            ACTION_UPDATE_PROPERTY, ACTION_SEND_COMMAND, ACTION_FORWARD_MQTT, ACTION_FORWARD_HTTP);

    // ---------- 执行状态 ----------

    /** 状态：待执行 / 重试中。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：执行成功（终态）。 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 状态：执行失败（终态，重试耗尽）。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 全部执行状态。 */
    public static final Set<String> STATUSES = Set.of(STATUS_PENDING, STATUS_SUCCESS, STATUS_FAILED);

    // ---------- 比较符 ----------

    public static final String OPERATOR_GT = "GT";
    public static final String OPERATOR_GTE = "GTE";
    public static final String OPERATOR_LT = "LT";
    public static final String OPERATOR_LTE = "LTE";
    public static final String OPERATOR_EQ = "EQ";
    public static final String OPERATOR_NE = "NE";

    /** 全部比较符。 */
    public static final Set<String> OPERATORS =
            Set.of(OPERATOR_GT, OPERATOR_GTE, OPERATOR_LT, OPERATOR_LTE, OPERATOR_EQ, OPERATOR_NE);

    /** 数值比较符：两侧需可解析为十进制数，按 {@code BigDecimal.compareTo} 比较。 */
    public static final Set<String> NUMERIC_OPERATORS =
            Set.of(OPERATOR_GT, OPERATOR_GTE, OPERATOR_LT, OPERATOR_LTE);

    // ---------- 事件类型 ----------

    /** 事件类型过滤取值（对应物模型事件 {@code eventType}）。 */
    public static final Set<String> EVENT_TYPES = Set.of("info", "alert", "fault");

    // ---------- 动作配置取值 ----------

    /** 转发 HTTP 支持的方法。 */
    public static final Set<String> HTTP_METHODS = Set.of("POST", "PUT");

    /** 转发 MQTT 支持的 QoS。 */
    public static final Set<Integer> MQTT_QOS = Set.of(0, 1, 2);

    // ---------- Webhook 事件类型 ----------

    /** Webhook 事件类型：规则触发（转发 HTTP 请求头 {@code X-Event-Type}）。 */
    public static final String EVENT_RULE_TRIGGERED = "rule.triggered";

    // ---------- 上限常量 ----------

    /** 规则名称最大长度。 */
    public static final int MAX_NAME_LENGTH = 64;
    /** 规则描述最大长度。 */
    public static final int MAX_DESCRIPTION_LENGTH = 255;
    /** 标识符最大长度（触发标识符与动作目标标识符）。 */
    public static final int MAX_IDENTIFIER_LENGTH = 64;
    /** 比较阈值最大长度。 */
    public static final int MAX_THRESHOLD_LENGTH = 255;
    /** 事件类型最大长度。 */
    public static final int MAX_EVENT_TYPE_LENGTH = 16;
    /** 转发 MQTT Topic 最大长度。 */
    public static final int MAX_TOPIC_LENGTH = 255;
    /** 转发 HTTP URL 最大长度。 */
    public static final int MAX_URL_LENGTH = 512;
    /** 转发 HTTP 请求头个数上限。 */
    public static final int MAX_HEADERS = 32;
    /** 转发 HTTP 请求头单值长度上限。 */
    public static final int MAX_HEADER_VALUE_LENGTH = 512;
    /** 冷却窗口取值上限（秒）。 */
    public static final int MAX_COOLDOWN_SECONDS = 86400;
    /** 单用户规则数上限（默认值，运行时可被 {@code app.rule.max-rules-per-user} 覆盖）。 */
    public static final int MAX_RULES_PER_USER = 200;

    // ---------- 模板占位符 ----------

    public static final String PLACEHOLDER_RULE_ID = "${ruleId}";
    public static final String PLACEHOLDER_RULE_NAME = "${ruleName}";
    public static final String PLACEHOLDER_EXECUTION_ID = "${executionId}";
    public static final String PLACEHOLDER_DEVICE_ID = "${deviceId}";
    public static final String PLACEHOLDER_DEVICE_KEY = "${deviceKey}";
    public static final String PLACEHOLDER_DEVICE_NAME = "${deviceName}";
    public static final String PLACEHOLDER_DEVICE_TYPE = "${deviceType}";
    public static final String PLACEHOLDER_SOURCE_TYPE = "${sourceType}";
    public static final String PLACEHOLDER_IDENTIFIER = "${identifier}";
    public static final String PLACEHOLDER_VALUE = "${value}";
    public static final String PLACEHOLDER_REPORTED_AT = "${reportedAt}";
    public static final String PLACEHOLDER_TIMESTAMP = "${timestamp}";

    /** 全部模板占位符（渲染器据此单次扫描替换，未列出的 {@code ${...}} 保留原样）。 */
    public static final Set<String> PLACEHOLDERS = Set.of(
            PLACEHOLDER_RULE_ID, PLACEHOLDER_RULE_NAME, PLACEHOLDER_EXECUTION_ID,
            PLACEHOLDER_DEVICE_ID, PLACEHOLDER_DEVICE_KEY, PLACEHOLDER_DEVICE_NAME,
            PLACEHOLDER_DEVICE_TYPE, PLACEHOLDER_SOURCE_TYPE, PLACEHOLDER_IDENTIFIER,
            PLACEHOLDER_VALUE, PLACEHOLDER_REPORTED_AT, PLACEHOLDER_TIMESTAMP);
}