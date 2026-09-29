package com.mqtt.cloud.mqtt;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.HistoryService;
import com.mqtt.cloud.service.MessageService;
import com.mqtt.cloud.service.RealtimeStreamService;
import com.mqtt.cloud.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 消息处理器
 * <p>
 * 订阅设备上行 Topic，完成设备状态刷新、消息落库、历史留存与 Webhook 事件分发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler implements MqttCallback {

    private static final String TOPIC_DATA = "data";
    private static final String TOPIC_HEARTBEAT = "heartbeat";
    private static final String TOPIC_LWT = "lwt";

    private static final String EVENT_DATA = "device.data";
    private static final String EVENT_HEARTBEAT = "device.heartbeat";
    private static final String EVENT_LWT = "device.lwt";

    private final MqttClientManager mqttClientManager;
    private final DeviceService deviceService;
    private final MessageService messageService;
    private final HistoryService historyService;
    private final WebhookDispatcher webhookDispatcher;
    private final RealtimeStreamService realtimeStreamService;

    /**
     * 应用就绪后再连接 Broker，避免阻塞启动流程。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        Map<String, Integer> topics = new LinkedHashMap<>();
        topics.put("device/+/data", 1);
        topics.put("device/+/heartbeat", 0);
        topics.put("device/+/lwt", 1);
        mqttClientManager.register(this, topics);
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        String payload = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
        log.debug("收到 MQTT 消息: topic={}, payload={}", topic, payload);

        String[] parts = topic.split("/");
        if (parts.length < 3) {
            log.warn("忽略格式不正确的 Topic: {}", topic);
            return;
        }
        String deviceKey = parts[1];
        String messageType = parts[2];

        Device device;
        try {
            device = deviceService.getDeviceByKey(deviceKey);
        } catch (BusinessException e) {
            log.warn("收到未注册设备的消息: deviceKey={}, topic={}", deviceKey, topic);
            return;
        }

        try {
            refreshDeviceStatus(device.getId(), messageType);
            messageService.saveReceivedMessage(device.getId(), topic, payload, mqttMessage.getQos());
            if (TOPIC_DATA.equals(messageType)) {
                historyService.saveHistoryRecord(device.getId(), topic, payload);
            }
            webhookDispatcher.dispatch(device.getId(), resolveEventType(messageType), device, payload);
        } catch (Exception e) {
            log.error("处理 MQTT 消息失败: topic={}", topic, e);
        }

        // 实时通道推送与主处理链路隔离：单个 SSE 订阅者异常不得影响 MQTT 消息处理
        try {
            realtimeStreamService.publish(device, topic, payload);
        } catch (Exception e) {
            log.warn("推送实时数据失败: topic={}", topic, e);
        }
    }

    /**
     * 设备状态变更与状态历史由 DeviceService 统一处理，此处不再重复写历史。
     */
    private void refreshDeviceStatus(Long deviceId, String messageType) {
        switch (messageType) {
            case TOPIC_DATA, TOPIC_HEARTBEAT -> deviceService.updateDeviceStatus(deviceId, DeviceStatusValue.ONLINE);
            case TOPIC_LWT -> deviceService.updateDeviceStatus(deviceId, DeviceStatusValue.OFFLINE);
            default -> log.debug("无需刷新设备状态的消息类型: {}", messageType);
        }
    }

    private String resolveEventType(String messageType) {
        return switch (messageType) {
            case TOPIC_HEARTBEAT -> EVENT_HEARTBEAT;
            case TOPIC_LWT -> EVENT_LWT;
            default -> EVENT_DATA;
        };
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接丢失，等待自动重连");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("MQTT 消息投递完成");
    }
}