package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布MQTT消息请求DTO
 */
@Data
public class PublishMessageDTO {

    @NotBlank(message = "Topic不能为空")
    @Size(max = 255, message = "Topic长度不能超过255个字符")
    private String topic;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 10000, message = "消息内容不能超过10000个字符")
    private String payload;

    @NotNull(message = "QoS不能为空")
    @Min(value = 0, message = "QoS取值必须为0/1/2")
    @Max(value = 2, message = "QoS取值必须为0/1/2")
    private Integer qos = 0;

    private Long deviceId;
}
