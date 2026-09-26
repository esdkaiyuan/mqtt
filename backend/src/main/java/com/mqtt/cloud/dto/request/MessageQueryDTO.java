package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 消息查询DTO（含分页和过滤）
 */
@Data
public class MessageQueryDTO {

    private String topic;
    private Long deviceId;
    private String direction;
    private String startTime;
    private String endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
