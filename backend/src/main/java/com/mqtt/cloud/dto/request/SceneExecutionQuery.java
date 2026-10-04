package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 场景执行记录查询 DTO（T-23 设计文档 §10.1）。
 * <p>
 * 供 {@code GET /scenes/executions} 绑定查询参数；所有过滤项均可空，空则不过滤。
 */
@Data
public class SceneExecutionQuery {

    /** 场景ID过滤，选填。 */
    private Long sceneId;

    /** 触发设备ID过滤，选填。 */
    private Long deviceId;

    /** 执行状态过滤：PENDING / RUNNING / SUCCESS / FAILED，选填。 */
    private String status;

    /** 页码，默认1。 */
    private Integer pageNum = 1;

    /** 每页数量，默认10，最大100。 */
    private Integer pageSize = 10;
}