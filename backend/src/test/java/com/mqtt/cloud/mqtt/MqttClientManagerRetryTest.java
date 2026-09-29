package com.mqtt.cloud.mqtt;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 MQTT 连接监督线程的韧性：首次连接失败后按退避持续重试，直至 Broker 可用；
 * 连接丢失时立即唤醒重试；关闭重连开关时只尝试一次即放弃。
 */
class MqttClientManagerRetryTest {

    private static final String TOPIC = "device/+/data";

    /** 用注入的替身替换真实 MqttClient，避免测试触碰网络。 */
    private static class TestableManager extends MqttClientManager {

        private final MqttClient client;

        TestableManager(MqttProperties properties, MeterRegistry registry, MqttClient client) {
            super(properties, registry);
            this.client = client;
        }

        @Override
        protected MqttClient newClient(String clientId) {
            return client;
        }
    }

    private MqttProperties properties(long initialDelay, long maxDelay, long probeInterval) {
        MqttProperties properties = new MqttProperties();
        properties.setHost("tcp://broker:1883");
        properties.setTimeout(1);
        properties.setReconnectInitialDelayMs(initialDelay);
        properties.setReconnectMaxDelayMs(maxDelay);
        properties.setConnectedProbeIntervalMs(probeInterval);
        return properties;
    }

    private MqttClient client(AtomicBoolean connected, AtomicInteger connectCalls, int failFirst,
                              AtomicReference<MqttCallback> capturedCallback) throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenAnswer(invocation -> connected.get());
        doAnswer(invocation -> {
            if (connectCalls.incrementAndGet() <= failFirst) {
                throw new MqttException(MqttException.REASON_CODE_BROKER_UNAVAILABLE);
            }
            connected.set(true);
            return null;
        }).when(client).connect(any(MqttConnectOptions.class));
        if (capturedCallback != null) {
            doAnswer(invocation -> {
                capturedCallback.set(invocation.getArgument(0));
                return null;
            }).when(client).setCallback(any(MqttCallback.class));
        }
        return client;
    }

    @Test
    void retries_until_broker_available_and_then_subscribes() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger connectCalls = new AtomicInteger();
        AtomicBoolean connected = new AtomicBoolean(false);
        MqttClient client = client(connected, connectCalls, 2, null);

        TestableManager manager = new TestableManager(properties(20, 40, 20), registry, client);
        manager.register(mock(MqttCallback.class), Map.of(TOPIC, 0));

        awaitUntil(manager::isConnected, 3000);

        assertThat(connectCalls.get()).isGreaterThanOrEqualTo(3);
        assertThat(registry.get("mqtt_connect_attempt_total").counter().count()).isGreaterThanOrEqualTo(3);
        assertThat(registry.get("mqtt_connect_failure_total").counter().count()).isGreaterThanOrEqualTo(2);
        assertThat(registry.get("mqtt_connected").gauge().value()).isEqualTo(1d);
        verify(client).subscribe(TOPIC, 0);

        manager.shutdown();
    }

    @Test
    void connection_lost_wakes_supervisor_without_waiting_for_probe() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger connectCalls = new AtomicInteger();
        AtomicBoolean connected = new AtomicBoolean(false);
        AtomicReference<MqttCallback> capturedCallback = new AtomicReference<>();
        MqttClient client = client(connected, connectCalls, 0, capturedCallback);

        // 巡检间隔刻意放大到 10s：若连接丢失未立即唤醒，重连不会在 1.5s 内发生
        TestableManager manager = new TestableManager(properties(20, 40, 10_000), registry, client);
        manager.register(mock(MqttCallback.class), Map.of(TOPIC, 0));

        awaitUntil(manager::isConnected, 3000);
        awaitUntil(() -> capturedCallback.get() != null, 3000);
        assertThat(connectCalls.get()).isEqualTo(1);

        connected.set(false);
        capturedCallback.get().connectionLost(new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT));

        awaitUntil(() -> connectCalls.get() >= 2, 1500);
        assertThat(manager.isConnected()).isTrue();

        manager.shutdown();
    }

    @Test
    void gives_up_after_first_failure_when_reconnect_disabled() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger connectCalls = new AtomicInteger();
        AtomicBoolean connected = new AtomicBoolean(false);
        MqttClient client = client(connected, connectCalls, Integer.MAX_VALUE, null);
        MqttProperties properties = properties(20, 40, 20);
        properties.setReconnect(false);

        TestableManager manager = new TestableManager(properties, registry, client);
        manager.register(mock(MqttCallback.class), Map.of(TOPIC, 0));

        awaitUntil(() -> registry.get("mqtt_connect_failure_total").counter().count() >= 1, 3000);
        Thread.sleep(200);

        assertThat(connectCalls.get()).isEqualTo(1);
        assertThat(manager.isConnected()).isFalse();
        manager.shutdown();
    }

    @Test
    void shutdown_closes_connected_client() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger connectCalls = new AtomicInteger();
        AtomicBoolean connected = new AtomicBoolean(false);
        MqttClient client = client(connected, connectCalls, 0, null);

        TestableManager manager = new TestableManager(properties(20, 40, 20), registry, client);
        manager.register(mock(MqttCallback.class), Map.of(TOPIC, 0));
        awaitUntil(manager::isConnected, 3000);

        manager.shutdown();

        verify(client).disconnect();
        verify(client).close();
        assertThat(manager.isConnected()).isFalse();
    }

    private static void awaitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("条件在 " + timeoutMs + " ms 内未满足");
    }
}