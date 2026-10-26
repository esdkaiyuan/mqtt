package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MFA 启用 / 停用请求（T-58）。
 */
@Data
public class MfaCodeRequest {

    @NotBlank(message = "动态口令不能为空")
    private String code;
}