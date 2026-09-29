package com.mqtt.cloud.mqtt;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 客户端管理器
 * <p>
 * 全局只维护一个 MqttClient，发布与订阅共用同一连接。连接由后台监督线程负责建立与恢复：
 * <ul>
 *     <li>连接按需建立，Broker 不可用时不会阻断应用启动</li>
 *     <li>连接失败按指数退避重试，直到成功（Paho 的 automaticReconnect 只覆盖“连上后断开”，
 *         不覆盖首次连接失败，故此处自行监督）</li>
 *     <li>连接丢失由回调立即唤醒监督线程，无需等待退避超时</li>
 *     <li>应用关闭时优雅断开并释放连接</li>
 * </ul>
 */
@Slf4j
@Component
public class MqttClientManager {

    private final MqttProperties properties;
    private final Object lock = new Object();
    private final Object reconnectSignal = new Object();

    private final Counter connectAttempts;
    private final Counter connectFailures;

    private MqttClient client;
    private volatile MqttCallback businessCallback;
    private volatile Map<String, Integer> subscriptions = Map.of();
    private volatile boolean shuttingDown = false;
    private volatile Thread supervisor;

    public MqttClientManager(MqttProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        Gauge.builder("mqtt_connected", this, manager -> manager.isConnected() ? 1 : 0)
                .description("MQTT 是否已连接：1 已连接 / 0 未连接")
                .register(meterRegistry);
        this.connectAttempts = Counter.builder("mqtt_connect_attempt_total")
                .description("MQTT 连接尝试次数（含首次与重连）")
                .register(meterRegistry);
        this.connectFailures = Counter.builder("mqtt_connect_failure_total")
                .description("MQTT 连接失败次数")
                .register(meterRegistry);
    }

    /**
     * 注册业务回调与订阅关系，并启动连接监督线程。
     * 连接失败只记录日志，监督线程会持续重试。
     */
    public void register(MqttCallback callback, Map<String, Integer> topicQos) {
        this.businessCallback = callback;
        this.subscriptions = Map.copyOf(new LinkedHashMap<>(topicQos));
        startSupervisor();
    }

    public void publish(String topic, String payload, int qos) throws MqttException {
        MqttClient connected = ensureConnected();
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(false);
        connected.publish(topic, message);
        log.debug("MQTT 消息已发布: topic={}, qos={}", topic, qos);
    }

    public boolean isConnected() {
        MqttClient current = this.client;
        return current != null && current.isConnected();
    }

    private void startSupervisor() {
        synchronized (lock) {
            if (shuttingDown || supervisor != null) {
                return;
            }
            Thread thread = new Thread(this::supervise, "mqtt-connector");
            thread.setDaemon(true);
            supervisor = thread;
            thread.start();
        }
    }

    /**
     * 连接监督：未连接时按指数退避重试，已连接时低频率巡检兜底。
     */
    private void supervise() {
        int attempt = 0;
        long delay = properties.getReconnectInitialDelayMs();
        while (!shuttingDown) {
            if (isConnected()) {
                attempt = 0;
                delay = properties.getReconnectInitialDelayMs();
                awaitReconnectSignal(properties.getConnectedProbeIntervalMs());
                continue;
            }
            attempt++;
            try {
                connectAttempts.increment();
                ensureConnected();
                log.info("MQTT 连接就绪: serverURI={}, 第 {} 次尝试", properties.getHost(), attempt);
                attempt = 0;
                delay = properties.getReconnectInitialDelayMs();
            } catch (Exception e) {
                connectFailures.increment();
                if (!properties.isReconnect()) {
                    log.error("MQTT 连接失败且 spring.mqtt.reconnect=false，放弃重连: {}", e.getMessage());
                    return;
                }
                log.warn("MQTT 连接失败（第 {} 次尝试），{} ms 后重试: {}", attempt, delay, e.getMessage());
                awaitReconnectSignal(delay);
                delay = Math.min(delay * 2, properties.getReconnectMaxDelayMs());
            }
        }
    }

    private void awaitReconnectSignal(long millis) {
        synchronized (reconnectSignal) {
            if (shuttingDown) {
                return;
            }
            try {
                reconnectSignal.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void signalReconnect() {
        synchronized (reconnectSignal) {
            reconnectSignal.notifyAll();
        }
    }

    private MqttClient ensureConnected() throws MqttException {
        synchronized (lock) {
            if (shuttingDown) {
                throw new MqttException(MqttException.REASON_CODE_CLIENT_CLOSED);
            }
            if (client == null) {
                client = createClient();
            }
            if (!client.isConnected()) {
                client.connect(buildOptions());
                subscribeAll(client);
            }
            return client;
        }
    }

    private MqttClient createClient() throws MqttException {
        String clientId = properties.getClientId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        MqttClient created = newClient(clientId);
        created.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT 连接丢失: {}", cause == null ? "unknown" : cause.getMessage());
                signalReconnect();
                MqttCallback callback = businessCallback;
                if (callback != null) {
                    callback.connectionLost(cause);
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                MqttCallback callback = businessCallback;
                if (callback != null) {
                    callback.messageArrived(topic, message);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                MqttCallback callback = businessCallback;
                if (callback != null) {
                    callback.deliveryComplete(token);
                }
            }
        });
        return created;
    }

    /** 客户端实例化入口，便于单元测试注入替身。 */
    protected MqttClient newClient(String clientId) throws MqttException {
        return new MqttClient(properties.getHost(), clientId, new MemoryPersistence());
    }

    private MqttConnectOptions buildOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(properties.getUsername());
        options.setPassword(properties.getPassword().toCharArray());
        options.setCleanSession(properties.isCleanSession());
        // 重连由本类的监督线程统一负责，避免与 Paho 的自动重连相互竞争
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(properties.getTimeout());
        options.setKeepAliveInterval(properties.getKeepalive());
        options.setMaxInflight(properties.getMaxInflight());
        return options;
    }

    private void subscribeAll(MqttClient target) throws MqttException {
        for (Map.Entry<String, Integer> entry : subscriptions.entrySet()) {
            target.subscribe(entry.getKey(), entry.getValue());
            log.info("MQTT 已订阅: topic={}, qos={}", entry.getKey(), entry.getValue());
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        signalReconnect();
        Thread thread = supervisor;
        if (thread != null) {
            try {
                // 等待监督线程退出，避免它与随后的 disconnect/close 争用同一个客户端
                thread.join(TimeUnit.SECONDS.toMillis(properties.getTimeout() + 5L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (lock) {
            if (client == null) {
                return;
            }
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
                log.info("MQTT 客户端已关闭");
            } catch (MqttException e) {
                log.warn("MQTT 客户端关闭异常: {}", e.getMessage());
            } finally {
                client = null;
            }
        }
    }
}