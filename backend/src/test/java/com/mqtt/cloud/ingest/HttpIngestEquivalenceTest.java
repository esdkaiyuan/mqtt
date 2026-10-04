package com.mqtt.cloud.ingest;

import com.mqtt.cloud.config.HttpIngestProperties;
import com.mqtt.cloud.mqtt.MqttClientManager;
import com.mqtt.cloud.mqtt.MqttMessageHandler;
import com.mqtt.cloud.mqtt.MqttProperties;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.MessageService;
import com.mqtt.cloud.service.RealtimeStreamService;
import com.mqtt.cloud.service.WebhookDispatcher;
import com.mqtt.cloud.service.impl.HttpIngestServiceImpl;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * HTTP 与 MQTT 上报的<b>等价性核心</b>测试（T-24 设计文档 §14.1、实施计划 P6）。
 * <p>
 * 本测试<b>不复制</b>任何一侧的构造逻辑，而是同时驱动两条真实代码路径：
 * <ul>
 *     <li>HTTP 侧：{@link HttpIngestServiceImpl#ingest} → 捕获投进 {@link IngestPipeline} 的 {@link IngestRecord}；</li>
 *     <li>MQTT 侧：{@link MqttMessageHandler#messageArrived}（等价 topic + UTF-8 载荷 + QoS）→ 捕获同管线投递的 {@code IngestRecord}。</li>
 * </ul>
 * 断言二者在同一 {@code (deviceKey, payload, messageType)} 下逐字段相等
 * （{@code deviceKey} / {@code topic} / {@code messageType} / {@code payload} / {@code qos}），
 * 仅 {@code receivedAt} 允许不等——这正是「HTTP 与 MQTT 同分片、同顺序、属性最新值等价」的技术支点
 * （路线图 L11 验收项 A1）。
 */
class HttpIngestEquivalenceTest {

    private static final String DEVICE_KEY = "dev001";

    /** 等价 topic：MQTT 真实上报主题，与 HTTP 侧合成的主题必须一致。 */
    private static String topicOf(String messageType) {
        return "device/" + DEVICE_KEY + "/" + messageType;
    }

    // ---------------------------------------------------------------- HTTP 侧

    /** 驱动真实 {@link HttpIngestServiceImpl}，捕获其投递的 {@link IngestRecord}。 */
    private IngestRecord overHttp(String messageType, String payload) {
        IngestPipeline pipeline = mock(IngestPipeline.class);
        HttpIngestServiceImpl service = new HttpIngestServiceImpl(
                pipeline, new IngestProperties(), new HttpIngestProperties());

        service.ingest(DEVICE_KEY, messageType, payload);

        ArgumentCaptor<IngestRecord> captor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(pipeline, times(1)).submit(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- MQTT 侧

    /** 驱动真实 {@link MqttMessageHandler}（等价 topic / UTF-8 载荷 / 投递 QoS），捕获其投递的 {@link IngestRecord}。 */
    private IngestRecord overMqtt(String messageType, String payload, int qos) {
        IngestPipeline pipeline = mock(IngestPipeline.class);
        MqttMessageHandler handler = new MqttMessageHandler(
                mock(MqttClientManager.class),
                new MqttProperties(),
                mock(DeviceService.class),
                mock(MessageService.class),
                mock(WebhookDispatcher.class),
                mock(RealtimeStreamService.class),
                pipeline,
                new IngestProperties());

        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        handler.messageArrived(topicOf(messageType), message);

        ArgumentCaptor<IngestRecord> captor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(pipeline, times(1)).submit(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- 用例

    static Stream<Arguments> equivalenceCases() {
        return Stream.of(
                Arguments.of("data", 1,
                        "{\"method\":\"thing.event.property.post\",\"params\":{\"power\":2,\"temperature\":23.5}}"),
                Arguments.of("heartbeat", 0, "{}"),
                Arguments.of("lwt", 1, "{}"),
                // 空 body 合法（§5.1）：HTTP 空串与 MQTT 空载荷等价
                Arguments.of("data", 1, ""));
    }

    @ParameterizedTest(name = "messageType={0} qos={1} 时 HTTP 与 MQTT 构造的 IngestRecord 逐字段等价")
    @MethodSource("equivalenceCases")
    void http_record_should_equal_mqtt_record_field_by_field(String messageType, int qos, String payload) {
        IngestRecord http = overHttp(messageType, payload);
        IngestRecord mqtt = overMqtt(messageType, payload, qos);

        String[] fields = {"deviceKey", "topic", "messageType", "payload", "qos"};
        assertThat(http)
                .as("除 receivedAt 外五字段应逐字段相等")
                .usingRecursiveComparison()
                .ignoringFields("receivedAt")
                .isEqualTo(mqtt);

        // 显式逐字段断言，失败时字段名可见
        assertThat(http.deviceKey()).as(fields[0]).isEqualTo(mqtt.deviceKey()).isEqualTo(DEVICE_KEY);
        assertThat(http.topic()).as(fields[1]).isEqualTo(mqtt.topic()).isEqualTo(topicOf(messageType));
        assertThat(http.messageType()).as(fields[2]).isEqualTo(mqtt.messageType()).isEqualTo(messageType);
        assertThat(http.payload()).as(fields[3]).isEqualTo(mqtt.payload()).isEqualTo(payload);
        assertThat(http.qos()).as(fields[4]).isEqualTo(mqtt.qos()).isEqualTo(qos);
    }

    @Test
    void receivedAt_is_the_only_field_allowed_to_differ_and_must_be_present_on_both_sides() {
        IngestRecord http = overHttp("data", "{\"power\":1}");
        IngestRecord mqtt = overMqtt("data", "{\"power\":1}", 1);

        assertThat(http.receivedAt()).isNotNull();
        assertThat(mqtt.receivedAt()).isNotNull();
    }

    @Test
    void http_synthesized_topic_matches_mqtt_topic_for_every_supported_type() {
        for (String type : new String[]{"data", "heartbeat", "lwt"}) {
            IngestRecord http = overHttp(type, "{}");
            assertThat(http.topic())
                    .as("messageType=%s 时合成 topic 应与 MQTT 主题一致", type)
                    .isEqualTo(topicOf(type));
        }
    }

    @Test
    void http_qos_mapping_aligns_with_mqtt_subscription_qos() {
        assertThat(overHttp("data", "{}").qos()).isEqualTo(1);
        assertThat(overHttp("heartbeat", "{}").qos()).isZero();
        assertThat(overHttp("lwt", "{}").qos()).isEqualTo(1);
    }

    @Test
    void device_key_shape_is_preserved_across_transport() {
        // 凭据用户名形如 {productKey}.{deviceKey}（DeviceSecretServiceImpl.buildUsername），
        // 但两种传输方式投进管线的 deviceKey 都是纯 deviceKey，不含 productKey 前缀与分隔点。
        assertThat(overHttp("data", "{}").deviceKey()).isEqualTo(DEVICE_KEY).doesNotContain(".");
        assertThat(overMqtt("data", "{}", 1).deviceKey()).isEqualTo(DEVICE_KEY).doesNotContain(".");
    }
}
