package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台审计日志（T-53）。
 * <p>
 * 只追加不修改，记录管理端与开放 API 的写操作。{@code user_id} 刻意不加外键：
 * 用户被删除后仍应保留其历史操作痕迹。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者用户 ID；匿名操作（设备激活等）为 null */
    private Long userId;

    /** 操作者用户名快照 */
    private String username;

    /** 操作者角色快照 */
    private String role;

    /** 来源：CONSOLE / OPEN_API / DEVICE / ANONYMOUS */
    private String source;

    private String httpMethod;

    private String path;

    /** 操作类型编码：CREATE / UPDATE / DELETE / COMMAND / ACK / LOGIN ... */
    private String action;

    /** 资源类型：DEVICE / PRODUCT / RULE / SCENE / OTA_TASK ... */
    private String resourceType;

    /** 资源标识：路径中的设备 ID / deviceKey / 批次号等 */
    private String resourceId;

    private Integer statusCode;

    private Boolean success;

    private String errorMessage;

    private String ip;

    private String userAgent;

    private Long durationMs;

    private LocalDateTime createdAt;
}