package com.mqtt.cloud.dto.response;

import lombok.Data;

/**
 * MFA 绑定信息（T-58）。
 * <p>
 * {@code secret} 与 {@code otpauthUri} 只在本次绑定过程中返回；平台不提供二次查询，
 * 用户需将其录入 authenticator 并回填动态口令确认。
 */
@Data
public class MfaSetupResponse {

    private String secret;

    private String otpauthUri;

    private String issuer;

    private Integer digits;

    private Integer periodSeconds;
}