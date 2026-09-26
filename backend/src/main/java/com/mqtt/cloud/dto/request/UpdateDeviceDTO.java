package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新设备请求DTO
 */
@Data
public class UpdateDeviceDTO {

    @Size(max = 100, message = "设备名称长度不能超过100个字符")
    private String deviceName;

    @Size(max = 50, message = "设备类型长度不能超过50个字符")
    private String deviceType;

    @Size(max = 255, message = "Topic长度不能超过255个字符")
    private String topic;

    @Size(max = 500, message = "设备描述长度不能超过500个字符")
    private String description;
}
