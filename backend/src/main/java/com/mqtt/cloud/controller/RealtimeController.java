package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.RealtimeStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 实时数据流接口。
 * <p>
 * 实际 URL：{@code GET /api/realtime/stream}（context-path=/api）。
 */
@Tag(name = "实时数据", description = "按用户权限过滤的设备实时数据流")
@RestController
@RequestMapping("/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeStreamService realtimeStreamService;

    @Operation(summary = "订阅实时数据流（SSE）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        return realtimeStreamService.subscribe(
                principal.getUserId(), "ADMIN".equals(principal.getRole()));
    }
}