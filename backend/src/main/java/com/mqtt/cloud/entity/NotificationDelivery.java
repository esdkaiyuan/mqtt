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
 * 通知投递记录实体（T-27 设计文档 §6.3）。
 * <p>
 * 对应数据库表：notification_delivery。一次「事件 × 渠道」投递尝试的留痕；
 * {@code status} / {@code attempt_count} / {@code next_attempt_at} 三列驱动重试巡检。
 * 内置通道（INAPP / WEBHOOK）无配置行，{@code channel_id} 为 NULL；渠道删除后该列由
 * 外键置 NULL，{@code channel_type} 已冗余在行上，留痕不失真。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("notification_delivery")
public class NotificationDelivery {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 渠道配置ID；内置通道为 NULL */
    @TableField("channel_id")
    private Long channelId;

    /** 渠道类型：INAPP / EMAIL / SMS / WEBHOOK */
    @TableField("channel_type")
    private String channelType;

    /** 事件名 */
    @TableField("event_type")
    private String eventType;

    /** 投递目标摘要（邮箱、脱敏手机号、URL 主机） */
    @TableField("target")
    private String target;

    /** 标题快照 */
    @TableField("title")
    private String title;

    /** 投递状态：SUCCESS / FAILED */
    @TableField("status")
    private String status;

    /** 已尝试次数 */
    @TableField("attempt_count")
    private Integer attemptCount;

    /** 下次重试时间；NULL 表示不再重试 */
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** 失败摘要 */
    @TableField("error_message")
    private String errorMessage;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}