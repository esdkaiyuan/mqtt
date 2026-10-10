package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知渠道配置实体（T-27 设计文档 §6.2）。
 * <p>
 * 对应数据库表：notification_channel。仅承载用户可配置的两类外部渠道（EMAIL / SMS）：
 * {@code config} 与 {@code events} 以 JSON 文本映射（与 {@code webhook_config} 同口径），
 * 敏感字段的脱敏与留空保留由服务层统一处理，实体本身只做列映射。
 * <p>
 * 内置通道（INAPP / WEBHOOK）不需要配置行，故不落本表。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("notification_channel")
public class NotificationChannelConfig {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 渠道类型：EMAIL / SMS */
    @TableField("channel_type")
    private String channelType;

    /** 渠道名称，单用户内唯一 */
    @TableField("name")
    private String name;

    /** 按类型不同的配置对象（JSON 文本） */
    @TableField("config")
    private String config;

    /** 订阅的事件名数组（JSON 文本） */
    @TableField("events")
    private String events;

    /** 是否启用：1=启用，0=停用 */
    @TableField("is_active")
    private Integer isActive;

    /** 最近成功投递时间 */
    @TableField("last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 连续失败次数 */
    @TableField("failure_count")
    private Integer failureCount;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}