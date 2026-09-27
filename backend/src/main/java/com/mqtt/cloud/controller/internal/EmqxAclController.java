package com.mqtt.cloud.controller.internal;

import com.mqtt.cloud.service.AclEvaluator;
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
public class EmqxAclController {

    private final AclEvaluator aclEvaluator;

    @Data
    public static class AclRequest {
        private String username;
        private String topic;
        private String action;
    }

    @PostMapping("/acl")
    public Map<String, String> acl(@RequestBody AclRequest request) {
        boolean allowed = aclEvaluator.allow(
                request.getUsername(), request.getAction(), request.getTopic());
        return Map.of("result", allowed ? "allow" : "deny");
    }
}