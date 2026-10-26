package com.mqtt.cloud.bridge;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.BridgeProperties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * {@link KafkaMessageForwarder} 单元测试（T-56）。
 */
class KafkaMessageForwarderTest {

    private BridgeProperties properties() {
        BridgeProperties properties = new BridgeProperties();
        properties.getKafka().setEnabled(true);
        properties.getKafka().setBootstrapServers("localhost:9092");
        properties.getKafka().setTopic("default-topic");
        return properties;
    }

    @Test
    void publish_should_send_with_topic_key_and_payload() {
        @SuppressWarnings("unchecked")
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        given(producer.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(null));
        KafkaMessageForwarder forwarder = new KafkaMessageForwarder(properties()) {
            @Override
            protected KafkaProducer<String, String> newProducer(Properties props) {
                return producer;
            }
        };

        forwarder.publish("factory.line1.alert", "dev-001", "{\"a\":1}");

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(producer).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("factory.line1.alert");
        assertThat(record.key()).isEqualTo("dev-001");
        assertThat(record.value()).isEqualTo("{\"a\":1}");

        forwarder.shutdown();
    }

    @Test
    void publish_should_fallback_to_default_topic() {
        @SuppressWarnings("unchecked")
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        given(producer.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(null));
        KafkaMessageForwarder forwarder = new KafkaMessageForwarder(properties()) {
            @Override
            protected KafkaProducer<String, String> newProducer(Properties props) {
                return producer;
            }
        };

        forwarder.publish("", null, "{}");

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(producer).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("default-topic");
        assertThat(captor.getValue().key()).isNull();

        forwarder.shutdown();
    }

    @Test
    void publish_should_throw_6323_when_disabled() {
        BridgeProperties properties = properties();
        properties.getKafka().setEnabled(false);
        KafkaMessageForwarder forwarder = new KafkaMessageForwarder(properties);

        assertThatThrownBy(() -> forwarder.publish("t", "k", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6323));
    }

    @Test
    void publish_should_throw_6323_when_bootstrap_missing() {
        BridgeProperties properties = properties();
        properties.getKafka().setBootstrapServers("");
        properties.getKafka().setTopic("");
        @SuppressWarnings("unchecked")
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        KafkaMessageForwarder forwarder = new KafkaMessageForwarder(properties) {
            @Override
            protected KafkaProducer<String, String> newProducer(Properties props) {
                return producer;
            }
        };

        assertThatThrownBy(() -> forwarder.publish("t", "k", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6323));
    }

    @Test
    void publish_should_throw_6323_when_send_fails() {
        @SuppressWarnings("unchecked")
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        given(producer.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        KafkaMessageForwarder forwarder = new KafkaMessageForwarder(properties()) {
            @Override
            protected KafkaProducer<String, String> newProducer(Properties props) {
                return producer;
            }
        };

        assertThatThrownBy(() -> forwarder.publish("t", "k", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6323));

        forwarder.shutdown();
    }
}