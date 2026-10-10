package com.mqtt.cloud.ingest;

import java.time.LocalDateTime;

/**
 * 上行消息的摄取载体。
 * 回调线程只负责构造它，后续所有 DB / HTTP 操作都在 worker 线程完成。
 *
 * @param traceId 本条消息的链路标识（T-30 E1）。HTTP 上报取自请求 traceId，
 *                MQTT 路径由接收方派生。worker 线程处理时放入 MDC，
 *                使「回调构造 → 落库 → 六路 fan-out」的日志可按同一 traceId 串联。
 *                为 null 时由worker 兜底生成。
 */
public record IngestRecord(
        String deviceKey,
        String topic,
        String messageType,
        String payload,
        int qos,
        LocalDateTime receivedAt,
        String traceId) {

    /** 兼容既有构造点（无 traceId），由 worker 补兜底值。 */
    public IngestRecord(String deviceKey, String topic, String messageType,
                        String payload, int qos, LocalDateTime receivedAt) {
        this(deviceKey, topic, messageType, payload, qos, receivedAt, null);
    }
}
