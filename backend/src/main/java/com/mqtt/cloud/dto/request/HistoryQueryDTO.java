package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 历史数据查询DTO（含分页和过滤）
 */
@Data
public class HistoryQueryDTO {

    private Long deviceId;
    private String topic;
    private String startTime;
    private String endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
