package com.mqtt.cloud.mqtt;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.service.DeviceService;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQTT 消息处理器
 * <p>
 * 默认路径只做「解析 Topic + 构造 {@link IngestRecord} + 入队」，DB 与 HTTP 操作全部交给
 * {@link IngestPipeline} 的 worker 线程；{@code app.ingest.enabled=false} 时回退为同步处理。
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
    private final MqttProperties mqttProperties;
    private final DeviceService deviceService;
    private final MessageService messageService;
    private final WebhookDispatcher webhookDispatcher;
    private final RealtimeStreamService realtimeStreamService;
    private final IngestPipeline ingestPipeline;
    private final IngestProperties ingestProperties;

    private final AtomicBoolean legacyPathWarned = new AtomicBoolean(false);

    /**
     * 应用就绪后再连接 Broker，避免阻塞启动流程。
     * <p>
     * 订阅使用 EMQX 共享订阅（{@code $share/{group}/{filter}}）：多副本部署时同一组内的副本
     * 分摊消息，避免每条上行被所有副本重复落库。单副本同样可用（组内仅一个成员），行为一致。
     * 共享订阅逻辑全在 Broker 侧，MQTT 3.1.1 客户端只需改写订阅主题，无需 MQTT 5。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        String group = mqttProperties.getSharedSubscriptionGroup();
        Map<String, Integer> topics = new LinkedHashMap<>();
        topics.put("$share/" + group + "/device/+/data", 1);
        topics.put("$share/" + group + "/device/+/heartbeat", 0);
        topics.put("$share/" + group + "/device/+/lwt", 1);
        // 命令回执：设备对下行命令的应答，落库后由 IngestDispatcher 旁路更新命令状态
        topics.put("$share/" + group + "/device/+/reply", 1);
        mqttClientManager.register(this, topics);
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        String payload = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
        String[] parts = topic.split("/");
        if (parts.length < 3) {
            log.warn("忽略格式不正确的 Topic: {}", topic);
            return;
        }

        IngestRecord record = new IngestRecord(
                parts[1], topic, parts[2], payload, mqttMessage.getQos(), LocalDateTime.now());

        if (!ingestProperties.isEnabled()) {
            legacySyncHandle(record);
            return;
        }
        ingestPipeline.submit(record);
    }

    /**
     * 回退路径：同步处理，供 R1 出问题时即时回滚，仅当 {@code app.ingest.enabled=false} 时使用。
     * 历史写入已在 R1-4 收敛到 {@code message} 表，此处不再写 {@code history_record}。
     */
    private void legacySyncHandle(IngestRecord record) {
        if (legacyPathWarned.compareAndSet(false, true)) {
            log.warn("app.ingest.enabled=false，上行消息走同步回退路径，仅用于故障回滚");
        }

        Device device;
        try {
            device = deviceService.getDeviceByKey(record.deviceKey());
        } catch (BusinessException e) {
            log.warn("收到未注册设备的消息: deviceKey={}, topic={}", record.deviceKey(), record.topic());
            return;
        }

        try {
            refreshDeviceStatus(device.getId(), record.messageType());
            messageService.saveReceivedMessage(device.getId(), record.topic(), record.payload(), record.qos());
            webhookDispatcher.dispatch(device.getId(), resolveEventType(record.messageType()), device, record.payload());
        } catch (Exception e) {
            log.error("处理 MQTT 消息失败: topic={}", record.topic(), e);
        }

        // 实时通道推送与主处理链路隔离：单个 SSE 订阅者异常不得影响 MQTT 消息处理
        try {
            realtimeStreamService.publish(device, record.topic(), record.payload());
        } catch (Exception e) {
            log.warn("推送实时数据失败: topic={}", record.topic(), e);
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