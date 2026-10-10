package com.mqtt.cloud.service.notification;

import com.mqtt.cloud.entity.Device;
import lombok.Data;

/**
 * 一次事件分发的渠道无关载荷（T-27 设计文档 §4.4 / §8.1）。
 * <p>
 * 由 {@code NotificationDispatcher} 组装后交给各渠道：站内信取 {@code level/title/content/refType/refId}，
 * 既有 Webhook 取 {@code device/payloadJson}（保持 T-17 信封不变），邮件与短信取 {@code title/content}
 * 与收件人默认值。
 */
@Data
public class NotificationMessage {

    /** 归属用户：站内信、渠道与投递记录一律按此隔离。 */
    private Long userId;

    /** 来源事件名，如 {@code alert.triggered}。 */
    private String eventType;

    /**
     * 目标渠道配置行 ID；内置通道为 {@code null}。
     * <p>
     * 分发器为每个命中的配置行填充该值，配置型渠道据此定位配置行；重试巡检亦用它把重投
     * 精确落到原渠道行上，避免重投到用户后来新增的其它渠道。
     */
    private Long channelId;

    /** 展示级别：INFO / WARNING / CRITICAL。 */
    private String level;

    /** 标题。 */
    private String title;

    /** 正文。 */
    private String content;

    /** 关联对象类型，本期为 ALERT。 */
    private String refType;

    /** 关联对象主键（告警记录 ID）。 */
    private Long refId;

    /** 关联设备快照；既有 Webhook 信封与投递目标摘要使用，为空表示设备已不存在。 */
    private Device device;

    /** 既有 Webhook 信封的 payload 字段（T-17 载荷 JSON 文本）。 */
    private String payloadJson;
}