package com.mqtt.cloud.mqtt;

import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.MessageService;
import com.mqtt.cloud.service.ProductService;
import com.mqtt.cloud.service.RealtimeStreamService;
import com.mqtt.cloud.service.TopicPrefixResolver;
import com.mqtt.cloud.service.WebhookDispatcher;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证订阅主题使用 EMQX 共享订阅前缀 {@code $share/{group}/}：多副本同一组内分摊消息，
 * 避免每条上行被所有副本重复落库；过滤器由产品主题模板生成，默认模板必须与既有
 * {@code device/+/...} 逐条一致。
 */
class MqttMessageHandlerTest {

    private final MqttClientManager clientManager = mock(MqttClientManager.class);
    private final DeviceService deviceService = mock(DeviceService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final WebhookDispatcher webhookDispatcher = mock(WebhookDispatcher.class);
    private final RealtimeStreamService realtimeStreamService = mock(RealtimeStreamService.class);
    private final IngestPipeline ingestPipeline = mock(IngestPipeline.class);
    private final IngestProperties ingestProperties = new IngestProperties();
    private final ProductService productService = mock(ProductService.class);

    private static Product enabledProduct(String productKey, String topicPrefix) {
        Product product = new Product();
        product.setProductKey(productKey);
        product.setTopicPrefix(topicPrefix);
        product.setStatus("ENABLED");
        return product;
    }

    @BeforeEach
    void setUp() {
        when(productService.listEnabled())
                .thenReturn(List.of(enabledProduct("legacy", "device/{deviceKey}")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> subscribeWith(String group) {
        MqttProperties properties = new MqttProperties();
        properties.setSharedSubscriptionGroup(group);
        MqttMessageHandler handler = new MqttMessageHandler(clientManager, properties, deviceService,
                messageService, webhookDispatcher, realtimeStreamService, ingestPipeline, ingestProperties,
                productService, new TopicPrefixResolver());
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

    /** 自定义模板的产品按自身层级生成过滤器，与默认模板产品并存。 */
    @Test
    void subscriptions_follow_product_topic_template() {
        when(productService.listEnabled()).thenReturn(List.of(
                enabledProduct("legacy", "device/{deviceKey}"),
                enabledProduct("esp32-fall", "{productKey}/{deviceKey}")));

        Map<String, Integer> topics = subscribeWith("mqtt-backend");

        assertThat(topics).containsOnlyKeys(
                "$share/mqtt-backend/device/+/data",
                "$share/mqtt-backend/device/+/heartbeat",
                "$share/mqtt-backend/device/+/lwt",
                "$share/mqtt-backend/device/+/reply",
                "$share/mqtt-backend/device/+/ota",
                "$share/mqtt-backend/esp32-fall/+/data",
                "$share/mqtt-backend/esp32-fall/+/heartbeat",
                "$share/mqtt-backend/esp32-fall/+/lwt",
                "$share/mqtt-backend/esp32-fall/+/reply",
                "$share/mqtt-backend/esp32-fall/+/ota");
    }

    /** 自定义模板下 deviceKey 位于倒数第二段，不能被当成 productKey。 */
    @Test
    void custom_topic_template_should_extract_device_key_from_last_two_segments() {
        MqttMessageHandler handler = new MqttMessageHandler(clientManager, new MqttProperties(), deviceService,
                messageService, webhookDispatcher, realtimeStreamService, ingestPipeline, ingestProperties,
                productService, new TopicPrefixResolver());
        MqttMessage message = new MqttMessage("{\"method\":\"thing.event.property.post\",\"params\":{}}"
                .getBytes(StandardCharsets.UTF_8));
        message.setQos(1);

        handler.messageArrived("esp32-fall/sensor-01/data", message);

        ArgumentCaptor<IngestRecord> captor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(ingestPipeline).submit(captor.capture());
        assertThat(captor.getValue().deviceKey()).isEqualTo("sensor-01");
        assertThat(captor.getValue().messageType()).isEqualTo("data");
        assertThat(captor.getValue().topic()).isEqualTo("esp32-fall/sensor-01/data");
    }

    /** 默认模板仍按原语义解析。 */
    @Test
    void default_topic_template_should_still_extract_device_key() {
        MqttMessageHandler handler = new MqttMessageHandler(clientManager, new MqttProperties(), deviceService,
                messageService, webhookDispatcher, realtimeStreamService, ingestPipeline, ingestProperties,
                productService, new TopicPrefixResolver());
        MqttMessage message = new MqttMessage("{}".getBytes(StandardCharsets.UTF_8));

        handler.messageArrived("device/sensor-01/heartbeat", message);

        ArgumentCaptor<IngestRecord> captor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(ingestPipeline).submit(captor.capture());
        assertThat(captor.getValue().deviceKey()).isEqualTo("sensor-01");
        assertThat(captor.getValue().messageType()).isEqualTo("heartbeat");
    }

    /** 无启用产品时不生成任何订阅，避免订阅到已停用产品的主题。 */
    @Test
    void no_subscription_when_no_enabled_product() {
        when(productService.listEnabled()).thenReturn(List.of());

        assertThat(subscribeWith("mqtt-backend")).isEmpty();
    }
}
