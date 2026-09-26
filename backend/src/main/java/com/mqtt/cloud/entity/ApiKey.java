package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * API密钥实体
 * 对应数据库表：api_key
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("api_key")
public class ApiKey {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    @TableField("user_id")
    private Long userId;

    /** API Key名称 */
    @TableField("name")
    private String name;

    /** API Key值（64位十六进制字符串） */
    @TableField("key_value")
    private String keyValue;

    /** 权限范围（JSON数组） */
    @TableField("permissions")
    private String permissions;

    /** 是否启用 */
    @TableField("is_active")
    private Integer isActive;

    /** 最后使用时间 */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    /** 过期时间，NULL为永不过期 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
