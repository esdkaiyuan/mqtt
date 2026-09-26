package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求DTO
 */
@Data
public class ChangePasswordDTO {
    /** 旧密码，必填 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码，必填，6-100字符 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度6-100个字符")
    private String newPassword;
}
