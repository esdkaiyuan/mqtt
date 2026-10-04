package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 场景查询 DTO（T-23 设计文档 §10.1）。
 * <p>
 * 供 {@code GET /scenes} 绑定查询参数；所有过滤项均可空，空则不过滤。
 */
@Data
public class SceneQuery {

    /** 触发源过滤：PROPERTY / EVENT / TIMER，选填。 */
    private String triggerType;

    /** 启用状态过滤，选填。 */
    private Boolean enabled;

    /** 关键字（名称或描述模糊），选填。 */
    private String keyword;

    /** 页码，默认1。 */
    private Integer pageNum = 1;

    /** 每页数量，默认10，最大100。 */
    private Integer pageSize = 10;
}