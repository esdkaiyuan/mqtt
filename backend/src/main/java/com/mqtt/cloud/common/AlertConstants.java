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

    /** 离线领域字段的取值上限（秒）：离线时长与抑制窗口共用。 */
    public static final int MAX_SECONDS = 86400;

    /** Webhook 事件类型：告警触发。 */
    public static final String EVENT_ALERT_TRIGGERED = "alert.triggered";
    /** Webhook 事件类型：告警恢复。 */
    public static final String EVENT_ALERT_RECOVERED = "alert.recovered";
}
