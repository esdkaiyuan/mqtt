package com.mqtt.cloud.mqtt;

import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.MessageService;
import com.mqtt.cloud.service.RealtimeStreamService;
import com.mqtt.cloud.service.WebhookDispatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 验证订阅主题使用 EMQX 共享订阅前缀 {@code $share/{group}/}：多副本同一组内分摊消息，
 * 避免每条上行被所有副本重复落库。
 */
class MqttMessageHandlerTest {

    private final MqttClientManager clientManager = mock(MqttClientManager.class);
    private final DeviceService deviceService = mock(DeviceService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final WebhookDispatcher webhookDispatcher = mock(WebhookDispatcher.class);
    private final RealtimeStreamService realtimeStreamService = mock(RealtimeStreamService.class);
    private final IngestPipeline ingestPipeline = mock(IngestPipeline.class);
    private final IngestProperties ingestProperties = new IngestProperties();

    @SuppressWarnings("unchecked")
    private Map<String, Integer> subscribeWith(String group) {
        MqttProperties properties = new MqttProperties();
        properties.setSharedSubscriptionGroup(group);
        MqttMessageHandler handler = new MqttMessageHandler(clientManager, properties, deviceService,
                messageService, webhookDispatcher, realtimeStreamService, ingestPipeline, ingestProperties);
        handler.subscribe();

        ArgumentCaptor<Map<String, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientManager).register(eq(handler), captor.capture());
        return captor.getValue();
    }

    @Test
    void subscribes_via_shared_subscription_group() {
        Map<String, Integer> topics = subscribeWith("mqtt-backend");

        assertThat(topics).containsOnlyKeys(
                "$share/mqtt-backend/device/+/data",
                "$share/mqtt-backend/device/+/heartbeat",
                "$share/mqtt-backend/device/+/lwt",
                "$share/mqtt-backend/device/+/reply",
                "$share/mqtt-backend/device/+/ota");
        assertThat(topics.get("$share/mqtt-backend/device/+/data")).isEqualTo(1);
        assertThat(topics.get("$share/mqtt-backend/device/+/heartbeat")).isEqualTo(0);
        assertThat(topics.get("$share/mqtt-backend/device/+/lwt")).isEqualTo(1);
        assertThat(topics.get("$share/mqtt-backend/device/+/reply")).isEqualTo(1);
        assertThat(topics.get("$share/mqtt-backend/device/+/ota")).isEqualTo(1);
    }

    @Test
    void shared_group_is_configurable() {
        Map<String, Integer> topics = subscribeWith("replica-set-a");

        assertThat(topics).containsOnlyKeys(
                "$share/replica-set-a/device/+/data",
                "$share/replica-set-a/device/+/heartbeat",
                "$share/replica-set-a/device/+/lwt",
                "$share/replica-set-a/device/+/reply",
                "$share/replica-set-a/device/+/ota");
    }
}