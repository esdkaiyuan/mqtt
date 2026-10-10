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
 * 设备证书（T-25）。
 * <p>
 * 一行代表一次签发，`serial` 全局唯一；`revoked_at` 非空表示已吊销。
 * 已吊销行保留不删，便于审计追溯；同一设备重新签发写新行，旧证书不自动吊销。
 * 表中不存私钥，设备私钥仅随签发响应返回一次。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_certificate")
public class DeviceCertificate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属设备（device.id） */
    @TableField("device_id")
    private Long deviceId;

    /** 产品键（冗余，供审计与模板比对） */
    @TableField("product_key")
    private String productKey;

    /** 设备键（冗余，供审计） */
    @TableField("device_key")
    private String deviceKey;

    /** 证书序列号（十六进制，全局唯一） */
    @TableField("serial")
    private String serial;

    /** 证书主体 DN，形如 CN={productKey}.{deviceKey},O=MQTT Cloud */
    @TableField("subject")
    private String subject;

    /** 签发时间（notBefore） */
    @TableField("issued_at")
    private LocalDateTime issuedAt;

    /** 失效时间（notAfter） */
    @TableField("not_after")
    private LocalDateTime notAfter;

    /** 吊销时间，NULL=未吊销 */
    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    /** 吊销原因（不超过 64 字符） */
    @TableField("revoke_reason")
    private String revokeReason;

    /** 创建时间，由 MybatisMetaObjectHandler 填充 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
