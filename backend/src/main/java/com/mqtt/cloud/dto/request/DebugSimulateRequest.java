package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 模拟上行请求（T-37）。
 * <p>
 * {@code messageType} 与 MQTT 侧一致：{@code data} / {@code heartbeat} / {@code lwt} / {@code reply} / {@code ota}。
 * {@code payload} 对 {@code data} / {@code reply} / {@code ota} 必须是 JSON 对象；心跳与遗嘱允许空。
 */
@Data
public class DebugSimulateRequest {

    @NotBlank(message = "消息类型不能为空")
    private String messageType;

    private JsonNode payload;
}
