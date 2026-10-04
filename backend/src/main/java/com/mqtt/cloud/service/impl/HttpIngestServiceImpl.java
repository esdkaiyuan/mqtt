package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.HttpIngestProperties;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.service.HttpIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * HTTP 上报接入服务实现（T-24 §8.3）。
 * <p>
 * 流程：校验 {@code messageType} 白名单 → 校验载荷大小 → 管线关闭则记 WARN 丢弃 →
 * 合成 topic → 构造与 MQTT 同形的 {@link IngestRecord} → {@link IngestPipeline#submit}。
 * <p>
 * 依赖仅 {@link IngestPipeline} / {@link IngestProperties} / {@link HttpIngestProperties}，
 * <b>不触碰任何 Mapper</b>；路由与落库全部复用既有摄取链路，下游零改动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HttpIngestServiceImpl implements HttpIngestService {

    /** HTTP 上报支持的消息类型，取值与 MQTT 侧 {@code MqttMessageHandler} 的 {@code TOPIC_*} 一致。 */
    static final Set<String> MESSAGE_TYPES = Set.of("data", "heartbeat", "lwt");

    private static final String TYPE_HEARTBEAT = "heartbeat";

    private final IngestPipeline ingestPipeline;
    private final IngestProperties ingestProperties;
    private final HttpIngestProperties httpIngestProperties;

    @Override
    public IngestAccepted ingest(String deviceKey, String messageType, String payload) {
        if (!MESSAGE_TYPES.contains(messageType)) {
            throw new BusinessException(ResultCode.INGEST_MESSAGE_TYPE_UNSUPPORTED);
        }
        int size = payload.getBytes(StandardCharsets.UTF_8).length;
        if (size > httpIngestProperties.getMaxPayloadBytes()) {
            throw new BusinessException(ResultCode.INGEST_PAYLOAD_TOO_LARGE);
        }

        LocalDateTime receivedAt = LocalDateTime.now();
        String topic = "device/" + deviceKey + "/" + messageType;

        if (!ingestProperties.isEnabled()) {
            // §8.5：与 MQTT 一致降级，但 HTTP 侧不做 legacySyncHandle 同步落库，避免污染 HTTP 超时。
            log.warn("摄取管线已关闭(app.ingest.enabled=false)，HTTP 上报直接丢弃: deviceKey={}, messageType={}, size={}",
                    deviceKey, messageType, size);
            return new IngestAccepted(deviceKey, messageType, topic, receivedAt, true);
        }

        IngestRecord record = new IngestRecord(deviceKey, topic, messageType, payload, qosOf(messageType), receivedAt);
        ingestPipeline.submit(record);
        return new IngestAccepted(deviceKey, messageType, topic, receivedAt, true);
    }

    /** QoS 映射与 MQTT 订阅 QoS 对齐：{@code heartbeat} → {@code 0}，其余（{@code data} / {@code lwt}）→ {@code 1}。 */
    private int qosOf(String messageType) {
        return TYPE_HEARTBEAT.equals(messageType) ? 0 : 1;
    }
}
