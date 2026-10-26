package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 审计日志查询条件（T-53）。
 */
@Data
public class AuditLogQueryDTO {

    /** 操作者用户 ID 精确过滤 */
    private Long userId;

    /** 用户名模糊过滤 */
    private String username;

    /** 操作类型：CREATE / UPDATE / DELETE / COMMAND / LOGIN ... */
    private String action;

    /** 资源类型：DEVICE / PRODUCT / RULE / ... */
    private String resourceType;

    /** 来源：CONSOLE / OPEN_API / DEVICE / ANONYMOUS */
    private String source;

    /** 是否成功 */
    private Boolean success;

    /** 路径 / 资源标识 / 用户名 模糊检索 */
    private String keyword;

    /** 起始时间，格式 yyyy-MM-dd HH:mm:ss 或 ISO-8601 */
    private String startTime;

    /** 结束时间，格式 yyyy-MM-dd HH:mm:ss 或 ISO-8601 */
    private String endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}