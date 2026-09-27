package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.RealtimeStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 实时通道实现。
 * <p>
 * 订阅者保存在内存 Map 中（单实例部署），推送时按“管理员或设备归属人”过滤，
 * 单个订阅者发送失败只摘除该订阅者，不影响其它订阅者与 MQTT 消息处理链路。
 */
@Slf4j
@Service
public class RealtimeStreamServiceImpl implements RealtimeStreamService {

    private final RealtimeProperties properties;

    /** 订阅者：userId -> 订阅信息，LinkedHashMap 保证推送顺序稳定 */
    private final Map<Long, Subscriber> subscribers = new LinkedHashMap<>();

    /** 最近一次 publish 实际推送到的用户 ID，仅供测试断言使用 */
    private List<Long> lastRecipientUserIds = List.of();

    /**
     * 测试用无参构造：使用默认配置（不超时）。
     */
    public RealtimeStreamServiceImpl() {
        this(new RealtimeProperties());
    }

    @Autowired
    public RealtimeStreamServiceImpl(RealtimeProperties properties) {
        this.properties = properties;
    }

    private record Subscriber(Long userId, boolean admin, SseEmitter emitter) {
    }

    @Override
    public synchronized SseEmitter subscribe(Long userId, boolean admin) {
        Subscriber previous = subscribers.remove(userId);
        if (previous != null) {
            completeQuietly(previous.emitter());
        }
        SseEmitter emitter = new SseEmitter(properties.getStreamTimeoutMs());
        emitter.onCompletion(() -> removeQuietly(userId));
        emitter.onTimeout(() -> removeQuietly(userId));
        emitter.onError(e -> removeQuietly(userId));
        subscribers.put(userId, new Subscriber(userId, admin, emitter));
        log.debug("SSE 订阅建立: userId={}, admin={}, 当前订阅数={}", userId, admin, subscribers.size());
        return emitter;
    }

    @Override
    public synchronized void publish(Device device, String topic, String payload) {
        if (subscribers.isEmpty()) {
            lastRecipientUserIds = List.of();
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("deviceId", device.getId());
        event.put("deviceKey", device.getDeviceKey());
        event.put("topic", topic);
        event.put("payload", payload);

        Long ownerId = device.getOwnerId();
        List<Long> recipients = new ArrayList<>();
        List<Long> dead = new ArrayList<>();
        for (Subscriber subscriber : subscribers.values()) {
            // 管理员接收全部；普通用户仅接收自己名下设备（无归属设备只推给管理员）
            boolean permitted = subscriber.admin()
                    || (ownerId != null && ownerId.equals(subscriber.userId()));
            if (!permitted) {
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event().name("device-data").data(event));
                recipients.add(subscriber.userId());
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 推送失败，摘除订阅者: userId={}", subscriber.userId());
                dead.add(subscriber.userId());
            }
        }
        lastRecipientUserIds = recipients;
        dead.forEach(this::removeQuietly);
    }

    private synchronized void removeQuietly(Long userId) {
        subscribers.remove(userId);
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            log.debug("关闭旧 SSE 连接失败", e);
        }
    }

    synchronized int debugSubscriberCount() {
        return subscribers.size();
    }

    synchronized List<Long> debugRecipientUserIds() {
        return List.copyOf(lastRecipientUserIds);
    }
}