package com.mqtt.cloud.service;

import java.time.LocalDateTime;

/**
 * HTTP 上报接入服务（T-24 §5.4 / §8.3）。
 * <p>
 * 把「HTTP 请求」翻译成与 MQTT 完全同形的 {@link com.mqtt.cloud.ingest.IngestRecord} 并投进既有摄取管线，
 * 做到下游零感知、零改动。本接口位于 {@code service} 包，不依赖任何 {@code mapper}。
 */
public interface HttpIngestService {

    /**
     * 受理一次 HTTP 上报。
     *
     * @param deviceKey   设备键（已由鉴权过滤器校验并写入请求属性）
     * @param messageType 消息类型，白名单 {@code data} / {@code heartbeat} / {@code lwt}
     * @param payload     请求体原文（UTF-8），允许为空串
     * @return 受理结果（{@code accepted} 恒为 {@code true}，语义为「已受理并入队」）
     * @throws com.mqtt.cloud.common.exception.BusinessException messageType 非白名单（{@code 6247}）
     *                                                             或载荷超限（{@code 6248}）
     */
    IngestAccepted ingest(String deviceKey, String messageType, String payload);

    /**
     * 受理结果。
     *
     * @param deviceKey   设备键
     * @param messageType 消息类型
     * @param topic       合成 topic，形如 {@code device/{deviceKey}/{messageType}}
     * @param receivedAt  服务端受理时刻，最终落 {@code message.sentAt}
     * @param accepted    是否受理（恒为 {@code true}）
     */
    record IngestAccepted(String deviceKey, String messageType, String topic,
                          LocalDateTime receivedAt, boolean accepted) {
    }
}
