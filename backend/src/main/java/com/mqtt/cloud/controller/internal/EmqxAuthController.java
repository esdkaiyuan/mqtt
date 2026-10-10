package com.mqtt.cloud.controller.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mqtt.cloud.service.EmqxAuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/emqx")
@RequiredArgsConstructor
public class EmqxAuthController {

    private final EmqxAuthService emqxAuthService;

    /**
     * EMQX HTTP 认证器的请求体。
     * <p>
     * 字段名与 EMQX 侧占位符一致：口令位在 JWT 模式下承载设备令牌，
     * 证书字段由 {@code ${cert_subject}} 与 {@code ${cert_common_name}} 填充，
     * 非 TLS 连接下缺失或为空串，因此两者都按可空处理。
     */
    @Data
    public static class AuthRequest {
        private String clientid;
        private String username;
        private String password;
        @JsonProperty("cert_subject")
        private String certSubject;
        @JsonProperty("cert_common_name")
        private String certCommonName;
    }

    @PostMapping("/auth")
    public Map<String, Object> auth(@RequestBody AuthRequest request) {
        boolean allowed = emqxAuthService.authenticate(
                request.getUsername(), request.getPassword(), request.getClientid(),
                request.getCertSubject(), request.getCertCommonName());
        return allowed
                ? Map.of("result", "allow", "is_superuser", false)
                : Map.of("result", "deny");
    }
}
