package com.mqtt.cloud.ingest;

import java.time.LocalDateTime;

/**
 * 上行消息的摄取载体。
 * 回调线程只负责构造它，后续所有 DB / HTTP 操作都在 worker 线程完成。
 */
public record IngestRecord(
        String deviceKey,
        String topic,
        String messageType,
        String payload,
        int qos,
        LocalDateTime receivedAt) {
}