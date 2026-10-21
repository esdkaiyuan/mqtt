package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.RealtimeStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

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

    @Operation(summary = "订阅实时数据流（SSE）", description =
            "可选 topics 过滤事件名：device-data（设备上行）/ device-log（设备日志增量）/ "
            + "property-trend（属性上报增量）。**不传 topics 即订阅全部主题**，"
            + "与改造前的单主题行为等价。管理端与任何非 ADMIN 用户只接收自己名下设备的事件。")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(description = "订阅的事件名，可重复；不传表示全部")
                    @RequestParam(required = false) Set<String> topics) {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        return realtimeStreamService.subscribe(
                principal.getUserId(), "ADMIN".equals(principal.getRole()), topics);
    }
}