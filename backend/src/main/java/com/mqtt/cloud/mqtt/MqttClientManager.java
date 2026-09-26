package com.mqtt.cloud.mqtt;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
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

/**
 * MQTT 客户端管理器
 * <p>
 * 全局只维护一个 MqttClient，发布与订阅共用同一连接：
 * <ul>
 *     <li>连接按需建立，Broker 不可用时不会阻断应用启动</li>
 *     <li>断线由 Paho 自动重连，重连成功后自动恢复订阅</li>
 *     <li>应用关闭时优雅断开并释放连接</li>
 * </ul>
 */
@Slf4j
@Component
public class MqttClientManager {

    private final MqttProperties properties;
    private final Object lock = new Object();

    private MqttClient client;
    private volatile MqttCallback businessCallback;
    private volatile Map<String, Integer> subscriptions = Map.of();
    private volatile boolean shuttingDown = false;

    public MqttClientManager(MqttProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册业务回调与订阅关系，并尝试建立连接。
     * 连接失败只记录日志，后续发布或订阅时会自动重试。
     */
    public void register(MqttCallback callback, Map<String, Integer> topicQos) {
        this.businessCallback = callback;
        this.subscriptions = Map.copyOf(new LinkedHashMap<>(topicQos));
        try {
            ensureConnected();
        } catch (MqttException e) {
            log.error("MQTT 初始连接失败，后续操作将自动重试: {}", e.getMessage());
        }
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
        MqttClient created = new MqttClient(properties.getHost(), clientId, new MemoryPersistence());
        created.setCallback(new MqttCallbackExtended() {

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT 连接就绪: serverURI={}, reconnect={}", serverURI, reconnect);
                if (reconnect) {
                    try {
                        subscribeAll(created);
                    } catch (MqttException e) {
                        log.error("MQTT 重连后恢复订阅失败", e);
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT 连接丢失: {}", cause == null ? "unknown" : cause.getMessage());
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

    private MqttConnectOptions buildOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(properties.getUsername());
        options.setPassword(properties.getPassword().toCharArray());
        options.setCleanSession(properties.isCleanSession());
        options.setAutomaticReconnect(properties.isReconnect());
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
        synchronized (lock) {
            shuttingDown = true;
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