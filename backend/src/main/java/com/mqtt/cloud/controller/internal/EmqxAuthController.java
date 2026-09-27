package com.mqtt.cloud.controller.internal;

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

    @Data
    public static class AuthRequest {
        private String clientid;
        private String username;
        private String password;
    }

    @PostMapping("/auth")
    public Map<String, Object> auth(@RequestBody AuthRequest request) {
        boolean allowed = emqxAuthService.authenticate(
                request.getUsername(), request.getPassword(), request.getClientid());
        return allowed
                ? Map.of("result", "allow", "is_superuser", false)
                : Map.of("result", "deny");
    }
}