package com.mqtt.cloud.mqtt;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.RuleProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 规则外部 MQTT 转发器（T-19 设计文档 §4.5 / §8.5）。
 * <p>
 * 与 {@link MqttClientManager} 并列的**独立出站客户端**：后者连平台自有 Broker 并订阅上行，
 * 本类只连**第三方 Broker** 且**不订阅任何主题**（纯出站）。一个 Paho {@code MqttClient} 只能连一个
 * Server URI，故不能复用自有连接；独立连接也避免第三方 Broker 的抖动污染平台自身 MQTT 连接。
 * <p>
 * {@code app.rule.mqtt.enabled=false}（默认）时不建连，{@link #publish} 直接抛业务异常；
 * 首次连接失败由规则动作的重试兜底，连接丢失由监督线程按指数退避重连。
 * <p>
 * 本类位于 {@code com.mqtt.cloud.mqtt} 包，按 ArchUnit 规则**不得依赖 {@code mapper}**。
 */
@Slf4j
@Component
public class RuleMqttForwarder {

    private static final long RECONNECT_INITIAL_DELAY_MS = 1000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30000L;
    private static final long CONNECTED_PROBE_INTERVAL_MS = 30000L;

    private final RuleProperties properties;
    private final Object lock = new Object();
    private final Object reconnectSignal = new Object();

    private MqttClient client;
    private volatile boolean shuttingDown = false;
    private volatile Thread supervisor;

    public RuleMqttForwarder(RuleProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("外部 MQTT 转发未启用（app.rule.mqtt.enabled=false）");
            return;
        }
        if (isBlank(properties.getMqtt().getBrokerUrl())) {
            log.warn("外部 MQTT 转发已启用但未配置 broker-url，FORWARD_MQTT 动作将失败");
            return;
        }
        Thread thread = new Thread(this::supervise, "rule-mqtt-connector");
        thread.setDaemon(true);
        supervisor = thread;
        thread.start();
    }

    /**
     * 发布转发消息（纯出站，不保留）。
     *
     * @throws BusinessException 外部 MQTT 转发未启用
     * @throws MqttException     连接或发布失败（交由动作重试兜底）
     */
    public void publish(String topic, String payload, int qos) throws MqttException {
        if (!properties.getMqtt().isEnabled()) {
            throw new BusinessException(ResultCode.RULE_INVALID, "外部 MQTT 转发未启用");
        }
        MqttClient connected = ensureConnected();
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(false);
        connected.publish(topic, message);
        log.debug("规则 MQTT 转发已发布: topic={}, qos={}", topic, qos);
    }

    public boolean isConnected() {
        MqttClient current = this.client;
        return current != null && current.isConnected();
    }

    private void supervise() {
        int attempt = 0;
        long delay = RECONNECT_INITIAL_DELAY_MS;
        while (!shuttingDown) {
            if (isConnected()) {
                attempt = 0;
                delay = RECONNECT_INITIAL_DELAY_MS;
                awaitSignal(CONNECTED_PROBE_INTERVAL_MS);
                continue;
            }
            attempt++;
            try {
                ensureConnected();
                log.info("外部 MQTT 转发连接就绪: brokerUrl={}, 第 {} 次尝试",
                        properties.getMqtt().getBrokerUrl(), attempt);
                attempt = 0;
                delay = RECONNECT_INITIAL_DELAY_MS;
            } catch (Exception e) {
                log.warn("外部 MQTT 转发连接失败（第 {} 次尝试），{} ms 后重试: {}",
                        attempt, delay, e.getMessage());
                awaitSignal(delay);
                delay = Math.min(delay * 2, RECONNECT_MAX_DELAY_MS);
            }
        }
    }

    private void awaitSignal(long millis) {
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

    private MqttClient ensureConnected() throws MqttException {
        synchronized (lock) {
            if (shuttingDown) {
                throw new MqttException(MqttException.REASON_CODE_CLIENT_CLOSED);
            }
            if (client == null) {
                client = newClient(buildClientId());
            }
            if (!client.isConnected()) {
                client.connect(buildOptions());
            }
            return client;
        }
    }

    /** 客户端实例化入口，便于单元测试注入替身。 */
    protected MqttClient newClient(String clientId) throws MqttException {
        return new MqttClient(properties.getMqtt().getBrokerUrl(), clientId, new MemoryPersistence());
    }

    private String buildClientId() {
        String base = properties.getMqtt().getClientId();
        return (base == null || base.isBlank() ? "mqtt-rule-forwarder" : base)
                + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MqttConnectOptions buildOptions() {
        RuleProperties.Mqtt mqtt = properties.getMqtt();
        MqttConnectOptions options = new MqttConnectOptions();
        if (!isBlank(mqtt.getUsername())) {
            options.setUserName(mqtt.getUsername());
        }
        if (!isBlank(mqtt.getPassword())) {
            options.setPassword(mqtt.getPassword().toCharArray());
        }
        options.setCleanSession(true);
        // 重连由本类监督线程统一负责，避免与 Paho 自动重连相互竞争
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(mqtt.getConnectTimeout());
        options.setKeepAliveInterval(mqtt.getKeepalive());
        return options;
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        synchronized (reconnectSignal) {
            reconnectSignal.notifyAll();
        }
        Thread thread = supervisor;
        if (thread != null) {
            try {
                thread.join((properties.getMqtt().getConnectTimeout() + 5L) * 1000L);
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
                log.info("外部 MQTT 转发客户端已关闭");
            } catch (MqttException e) {
                log.warn("外部 MQTT 转发客户端关闭异常: {}", e.getMessage());
            } finally {
                client = null;
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}