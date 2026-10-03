package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 规则执行记录查询 DTO（T-19 设计文档 §10.1）。
 * <p>
 * 供 {@code GET /rules/executions} 绑定查询参数；所有过滤项均可空，空则不过滤；
 * 列表按 {@code created_at} 倒序。
 */
@Data
public class ExecutionQueryDTO {

    /** 规则ID过滤，选填 */
    private Long ruleId;

    /** 设备ID过滤，选填 */
    private Long deviceId;

    /** 状态过滤：PENDING / SUCCESS / FAILED，选填 */
    private String status;

    /** 页码，默认1 */
    private Integer pageNum = 1;

    /** 每页数量，默认10，最大100 */
    private Integer pageSize = 10;
}