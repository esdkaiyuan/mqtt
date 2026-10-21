package com.mqtt.cloud.common;

import java.util.Set;

/**
 * 告警中心共享常量（T-17 设计文档 §3 / §7 / §10.3）。
 * <p>
 * 集中来源 / 级别 / 状态 / 比较符 / 事件类型 / Webhook 事件名的字符串口径，
 * 避免评估、服务、通知三处各自散落字面量而产生漂移。
 */
public final class AlertConstants {

    private AlertConstants() {
    }

    /** 来源：属性阈值。 */
    public static final String SOURCE_THRESHOLD = "THRESHOLD";
    /** 来源：设备离线。 */
    public static final String SOURCE_OFFLINE = "OFFLINE";
    /** 来源：事件上报。 */
    public static final String SOURCE_EVENT = "EVENT";

    /** 全部来源类型。 */
    public static final Set<String> SOURCES = Set.of(SOURCE_THRESHOLD, SOURCE_OFFLINE, SOURCE_EVENT);

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    /** 全部级别，缺省 {@link #SEVERITY_WARNING}。 */
    public static final Set<String> SEVERITIES = Set.of(SEVERITY_INFO, SEVERITY_WARNING, SEVERITY_CRITICAL);

    public static final String STATUS_TRIGGERED = "TRIGGERED";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATUS_RECOVERED = "RECOVERED";

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

    /** 事件类型过滤取值（对应物模型事件 {@code eventType}）。 */
    public static final Set<String> EVENT_TYPES = Set.of("info", "alert", "fault");

    /** 离线领域字段的取值上限（秒）：离线时长、抑制窗口与统计窗口共用。 */
    public static final int MAX_SECONDS = 86400;

    // ---------- 阈值窗口聚合（T-52） ----------

    public static final String AGGREGATE_AVG = "AVG";
    public static final String AGGREGATE_MIN = "MIN";
    public static final String AGGREGATE_MAX = "MAX";
    public static final String AGGREGATE_SUM = "SUM";
    public static final String AGGREGATE_COUNT = "COUNT";

    /** 全部窗口聚合方式。 */
    public static final Set<String> AGGREGATES =
            Set.of(AGGREGATE_AVG, AGGREGATE_MIN, AGGREGATE_MAX, AGGREGATE_SUM, AGGREGATE_COUNT);

    /** 单条规则窗口内最少样本数上限。 */
    public static final int MAX_MIN_SAMPLES = 1000;

    /** Webhook 事件类型：告警触发。 */
    public static final String EVENT_ALERT_TRIGGERED = "alert.triggered";
    /** Webhook 事件类型：告警恢复。 */
    public static final String EVENT_ALERT_RECOVERED = "alert.recovered";
    /** Webhook 事件类型：告警升级（T-27）。 */
    public static final String EVENT_ALERT_ESCALATED = "alert.escalated";

    // ---------- 作用域档位（T-28） ----------

    /** 作用域：该用户全部设备（等价于既有 {@code device_id IS NULL}）。 */
    public static final String SCOPE_ALL = "ALL";
    /** 作用域：单台设备（等价于既有 {@code device_id} 非空）。 */
    public static final String SCOPE_DEVICE = "DEVICE";
    /** 作用域：选中分组及其全部后代分组内的设备。 */
    public static final String SCOPE_GROUP = "GROUP";
    /** 作用域：选中标签关联的设备。 */
    public static final String SCOPE_TAG = "TAG";

    /** 全部作用域档位。 */
    public static final Set<String> SCOPES = Set.of(SCOPE_ALL, SCOPE_DEVICE, SCOPE_GROUP, SCOPE_TAG);
}
