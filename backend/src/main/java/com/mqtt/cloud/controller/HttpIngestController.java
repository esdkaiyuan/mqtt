package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.service.HttpIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP 上报端点（T-24 多协议接入，设计文档 §10.1）。
 * <p>
 * 设备以 HTTP Basic 携带凭据（用户名 {@code productKey.deviceKey}，密码为设备密钥），
 * 由 {@code DeviceCredentialAuthFilter} 完成鉴权；本控制器仅负责把请求转交
 * {@link HttpIngestService}，由后者构造与 MQTT <b>同形</b> 的 {@code IngestRecord} 投进既有摄取管线，
 * 从而做到下游零感知、零改动。
 * <p>
 * 开关：{@code app.http-ingest.enabled}（默认 {@code true}）。关闭时本控制器不注册，
 * {@code /ingest/**} 将直接返回 404。
 */
@Tag(name = "HTTP 设备上报", description = "设备经 HTTP 上报数据 / 心跳 / 遗嘱，与 MQTT 汇聚进同一摄取管线")
@ConditionalOnProperty(prefix = "app.http-ingest", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/ingest")
public class HttpIngestController {

    private final HttpIngestService httpIngestService;

    public HttpIngestController(HttpIngestService httpIngestService) {
        this.httpIngestService = httpIngestService;
    }

    @Operation(summary = "设备上报", description = "messageType 白名单 data / heartbeat / lwt；"
            + "凭据非法返回 401 + 6246，类型不支持返回 400 + 6247，载荷超限返回 413 + 6248")
    @PostMapping("/{deviceKey}/{messageType}")
    public Result<HttpIngestService.IngestAccepted> ingest(
            @Parameter(description = "设备 Key", required = true) @PathVariable String deviceKey,
            @Parameter(description = "消息类型：data / heartbeat / lwt", required = true) @PathVariable String messageType,
            @RequestBody(required = false) String payload) {
        return Result.success(httpIngestService.ingest(deviceKey, messageType, payload == null ? "" : payload));
    }
}
