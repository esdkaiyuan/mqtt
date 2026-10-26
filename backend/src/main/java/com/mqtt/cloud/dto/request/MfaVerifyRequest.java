package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 二步登录验证请求（T-58）。
 */
@Data
public class MfaVerifyRequest {

    @NotBlank(message = "二步验证会话不能为空")
    private String mfaToken;

    @NotBlank(message = "动态口令不能为空")
    private String code;
}