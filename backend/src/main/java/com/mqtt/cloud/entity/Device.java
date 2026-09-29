package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备实体
 * 对应数据库表：device
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device")
public class Device {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备显示名称 */
    @TableField("device_name")
    private String deviceName;

    /** 设备唯一标识符 */
    @TableField("device_key")
    private String deviceKey;

    /** 设备类型：sensor/gateway/actuator */
    @TableField("device_type")
    private String deviceType;

    /** MQTT Topic路径 */
    @TableField("topic")
    private String topic;

    /** 设备描述 */
    @TableField("description")
    private String description;

    /** 所属用户ID */
    @TableField("owner_id")
    private Long ownerId;

    /** 设备状态：ONLINE/OFFLINE/INACTIVE */
    @TableField("status")
    private String status;

    /** 最后上报时间 */
    @TableField("last_seen")
    private LocalDateTime lastSeen;

    /** 设备扩展元数据（JSON格式） */
    @TableField("metadata")
    private String metadata;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记（0=未删除，1=已删除） */
    @TableField("deleted")
    private Integer deleted;

    /** 所属产品ID */
    @TableField("product_id")
    private Long productId;

    /** 一机一密密钥哈希（BCrypt），永不对外序列化 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @TableField("device_secret_hash")
    private String deviceSecretHash;

    /** 密钥重置时间 */
    @TableField("secret_updated_at")
    private LocalDateTime secretUpdatedAt;

    /** 连接许可：1=允许，0=禁止（与运行态 status 语义分离） */
    @TableField("enabled")
    private Integer enabled;

    // 以下字段用于DTO映射，不映射到数据库
    @TableField(exist = false)
    private String ownerUsername;
}
