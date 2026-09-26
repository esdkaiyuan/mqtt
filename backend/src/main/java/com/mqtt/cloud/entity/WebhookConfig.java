package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Webhook配置实体
 * 对应数据库表：webhook_config
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("webhook_config")
public class WebhookConfig {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 关联设备ID，NULL表示监听所有设备 */
    @TableField("device_id")
    private Long deviceId;

    /** Webhook名称 */
    @TableField("name")
    private String name;

    /** 回调URL */
    @TableField("url")
    private String url;

    /** 签名密钥（HMAC-SHA256） */
    @TableField("secret")
    private String secret;

    /** 事件类型列表（JSON数组） */
    @TableField("events")
    private String events;

    /** 自定义HTTP请求头（JSON对象） */
    @TableField("headers")
    private String headers;

    /** 失败重试次数 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 请求超时秒数 */
    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    /** 是否启用 */
    @TableField("is_active")
    private Integer isActive;

    /** 最后触发时间 */
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
