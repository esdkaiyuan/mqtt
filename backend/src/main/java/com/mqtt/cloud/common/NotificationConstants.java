package com.mqtt.cloud.common;

import java.util.Set;

/**
 * 通知渠道扩展共享常量（T-27 设计文档 §3 / §4）。
 * <p>
 * 集中渠道类型 / 关联对象类型 / 投递状态的字符串口径，避免分发、渠道实现与投递留痕三处
 * 各自散落字面量而产生漂移。
 */
public final class NotificationConstants {

    private NotificationConstants() {
    }

    /** 内置通道：站内信（落库即达，必达通道）。 */
    public static final String CHANNEL_INAPP = "INAPP";
    /** 配置型通道：邮件。 */
    public static final String CHANNEL_EMAIL = "EMAIL";
    /** 配置型通道：短信。 */
    public static final String CHANNEL_SMS = "SMS";
    /** 内置通道：既有 Webhook 出口的适配器。 */
    public static final String CHANNEL_WEBHOOK = "WEBHOOK";

    /** 全部渠道类型。 */
    public static final Set<String> CHANNELS =
            Set.of(CHANNEL_INAPP, CHANNEL_EMAIL, CHANNEL_SMS, CHANNEL_WEBHOOK);

    /** 用户可配置的渠道类型：只有这两类有 {@code notification_channel} 配置行。 */
    public static final Set<String> CONFIGURED_CHANNELS = Set.of(CHANNEL_EMAIL, CHANNEL_SMS);

    /** 通知事件白名单：渠道配置行的 {@code events} 只能取这三者。 */
    public static final Set<String> NOTIFICATION_EVENTS = Set.of(
            AlertConstants.EVENT_ALERT_TRIGGERED,
            AlertConstants.EVENT_ALERT_RECOVERED,
            AlertConstants.EVENT_ALERT_ESCALATED);

    /** 渠道配置敏感字段的响应脱敏串；更新时回填该串表示保留原值。 */
    public static final String MASK = "******";

    /** 渠道配置中的敏感字段名：响应一律脱敏，更新留空或回填脱敏串时保留原值。 */
    public static final Set<String> SENSITIVE_CONFIG_KEYS =
            Set.of("password", "secret", "token", "apiKey", "headers");

    /** 关联对象类型：告警记录。 */
    public static final String REF_TYPE_ALERT = "ALERT";

    /** 投递状态：成功。 */
    public static final String DELIVERY_SUCCESS = "SUCCESS";
    /** 投递状态：失败（待重试或已达上限）。 */
    public static final String DELIVERY_FAILED = "FAILED";
}