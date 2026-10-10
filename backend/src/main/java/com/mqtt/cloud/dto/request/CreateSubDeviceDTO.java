package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建子设备请求DTO（T-26）。
 * <p>
 * 与 {@link CreateDeviceDTO} 字段对齐，去掉 {@code productId}：产品由路径上的网关给定，
 * 子设备与网关同产品为硬约束；父网关同样取自路径参数，不在请求体中传递。
 */
@Data
public class CreateSubDeviceDTO {

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称长度不能超过100个字符")
    private String deviceName;

    @NotBlank(message = "设备标识不能为空")
    @Size(max = 100, message = "设备标识长度不能超过100个字符")
    private String deviceKey;

    @NotBlank(message = "设备类型不能为空")
    @Size(max = 50, message = "设备类型长度不能超过50个字符")
    private String deviceType;

    @NotBlank(message = "MQTT Topic不能为空")
    @Size(max = 255, message = "Topic长度不能超过255个字符")
    private String topic;

    @Size(max = 500, message = "设备描述长度不能超过500个字符")
    private String description;
}
