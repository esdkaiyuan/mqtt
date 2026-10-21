package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上位机列表摘要（T-38）。
 */
@Data
public class HmiSummaryVO {

    private Long id;
    private String name;
    private Long deviceId;
    private String deviceKey;
    private String deviceName;
    private Integer widgetCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
