package com.mqtt.cloud.service.notification;

/**
 * 通知渠道抽象（T-27 设计文档 §4）。
 * <p>
 * 实现类均为 Spring Bean，由 {@code NotificationDispatcher} 收集后逐一分发。
 * 「事件是否匹配」由渠道自行判定：配置型渠道按配置行上的 {@code events} 过滤，不匹配时返回
 * {@link ChannelSendResult#skipped()}，由分发器决定不写投递记录。
 */
public interface NotificationChannel {

    /** 渠道类型：INAPP / EMAIL / SMS / WEBHOOK 之一。 */
    String channelType();

    /**
     * 投递一条消息。
     * <p>
     * 投递失败应通过 {@link ChannelSendResult#failure} 表达而非抛异常；分发器仍会对逃逸的异常
     * 做逐渠道隔离兜底。
     */
    ChannelSendResult send(NotificationMessage message);
}