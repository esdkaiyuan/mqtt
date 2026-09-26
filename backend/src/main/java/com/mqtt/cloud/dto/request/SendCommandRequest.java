package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送设备指令请求DTO
 */
@Data
public class SendCommandRequest {

    @NotBlank(message = "指令内容不能为空")
    @Size(max = 10000, message = "指令内容不能超过10000个字符")
    private String payload; // JSON格式指令

    @NotNull(message = "QoS等级不能为空")
    @Min(value = 0, message = "QoS取值必须为0/1/2")
    @Max(value = 2, message = "QoS取值必须为0/1/2")
    private Integer qos; // 0/1/2

    private Integer retain; // 是否保留消息，默认0
}
