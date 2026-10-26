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
     * 受理一次 HTTP 上报（不带幂等键）。
     */
    IngestAccepted ingest(String deviceKey, String messageType, String payload);

    /**
     * 受理一次 HTTP 上报，支持幂等键（T-53）。
     *
     * @param idempotencyKey 请求头 {@code Idempotency-Key}，可空；非空时同一设备 + 同一键
     *                       在 TTL 内只入队一次
     * @return 受理结果；重复请求返回 {@code accepted=false} 且 {@code receivedAt} 为首次受理时间
     * @throws com.mqtt.cloud.common.exception.BusinessException messageType 非白名单（{@code 6247}）、
     *                                                             载荷超限（{@code 6248}）、
     *                                                             超出频率（{@code 6314}）
     *                                                             或幂等键非法（{@code 6315}）
     */
    IngestAccepted ingest(String deviceKey, String messageType, String payload, String idempotencyKey);

    /**
     * 受理结果。
     *
     * @param deviceKey   设备键
     * @param messageType 消息类型
     * @param topic       合成 topic，形如 {@code device/{deviceKey}/{messageType}}
     * @param receivedAt  服务端受理时刻，最终落 {@code message.sentAt}
     * @param accepted    是否本次受理并入队；幂等重复请求为 {@code false}
     */
    record IngestAccepted(String deviceKey, String messageType, String topic,
                          LocalDateTime receivedAt, boolean accepted) {
    }
}
