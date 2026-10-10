package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备激活请求（T-30）。
 * <p>
 * 设备首次连接时提交激活码；也允许控制台侧以管理员身份代为激活。
 */
@Data
public class ActivateDeviceDTO {

    @NotBlank(message = "设备标识不能为空")
    @Size(max = 100, message = "设备标识长度不能超过100个字符")
    private String deviceKey;

    @NotBlank(message = "激活码不能为空")
    @Size(max = 64, message = "激活码长度不能超过64个字符")
    private String activateCode;
}
