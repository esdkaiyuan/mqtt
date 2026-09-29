package com.mqtt.cloud.ingest;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.RealtimeStreamService;
import com.mqtt.cloud.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 落库成功后的下游分发：Webhook 事件与 SSE 实时推送。
 * <p>
 * 由 worker 在落库事务提交后调用，保证「推送出去的，库里一定已经有」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestDispatcher {

    private static final String TOPIC_HEARTBEAT = "heartbeat";
    private static final String TOPIC_LWT = "lwt";

    private static final String EVENT_DATA = "device.data";
    private static final String EVENT_HEARTBEAT = "device.heartbeat";
    private static final String EVENT_LWT = "device.lwt";

    private final WebhookDispatcher webhookDispatcher;
    private final RealtimeStreamService realtimeStreamService;

    public void dispatch(List<ResolvedEvent> events) {
        for (ResolvedEvent event : events) {
            Device device = event.device();
            IngestRecord record = event.record();
            try {
                webhookDispatcher.dispatch(device.getId(), resolveEventType(record.messageType()), device, record.payload());
            } catch (Exception e) {
                log.warn("Webhook 事件分发失败: topic={}", record.topic(), e);
            }
            try {
                realtimeStreamService.publish(device, record.topic(), record.payload());
            } catch (Exception e) {
                log.warn("推送实时数据失败: topic={}", record.topic(), e);
            }
        }
    }

    private String resolveEventType(String messageType) {
        return switch (messageType) {
            case TOPIC_HEARTBEAT -> EVENT_HEARTBEAT;
            case TOPIC_LWT -> EVENT_LWT;
            default -> EVENT_DATA;
        };
    }
}