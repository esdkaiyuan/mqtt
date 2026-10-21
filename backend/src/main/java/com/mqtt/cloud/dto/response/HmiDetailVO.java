package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上位机详情（T-38）：配置已由服务层解析为结构化模型。
 */
@Data
public class HmiDetailVO {

    private Long id;
    private String name;
    private Long deviceId;
    private String deviceKey;
    private String deviceName;
    /** 运行 / 编辑所需的只读物模型投影 */
    private HmiModelVO model;
    private HmiConfig config;
    private Integer widgetCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
