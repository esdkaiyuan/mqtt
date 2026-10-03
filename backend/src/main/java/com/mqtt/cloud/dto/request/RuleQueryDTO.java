package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 规则查询 DTO（T-19 设计文档 §10.1）。
 * <p>
 * 供 {@code GET /rules} 绑定查询参数；所有过滤项均可空，空则不过滤。
 */
@Data
public class RuleQueryDTO {

    /** 触发源过滤：PROPERTY / EVENT，选填 */
    private String sourceType;

    /** 动作类型过滤：UPDATE_PROPERTY / SEND_COMMAND / FORWARD_MQTT / FORWARD_HTTP，选填 */
    private String actionType;

    /** 启用状态过滤，选填 */
    private Boolean enabled;

    /** 关键字（名称或描述模糊），选填 */
    private String keyword;

    /** 页码，默认1 */
    private Integer pageNum = 1;

    /** 每页数量，默认10，最大100 */
    private Integer pageSize = 10;
}