package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.RealtimeStreamService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 实时通道实现。
 * <p>
 * 订阅者保存在内存中（同一用户可持有多个连接），推送时按“管理员或设备归属人”过滤。
 * 投递在独立线程完成，调用方（MQTT 摄取线程 / 后续的 Redis 订阅线程）不会被慢客户端阻塞；
 * 单个连接发送失败只摘除该连接，不影响其它订阅者与消息处理链路。
 * 另按固定间隔下发 SSE 注释帧作为心跳，既穿透网关空闲超时，也用于探测已死的连接。
 */
@Slf4j
@Service
public class RealtimeStreamServiceImpl implements RealtimeStreamService {

    private static final String EVENT_DATA = "device-data";
    /** 设备日志增量（T-36 / X-3）。 */
    public static final String EVENT_DEVICE_LOG = "device-log";
    /** 属性上报增量（T-36 / X-3），供趋势页实时追加。 */
    public static final String EVENT_PROPERTY_TREND = "property-trend";
    private static final String HEARTBEAT_COMMENT = "hb";

    private final RealtimeProperties properties;

    /** 订阅者：userId -> 该用户的全部连接 */
    private final Map<Long, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /** 推送线程：与消息链路解耦，单线程以保持同一设备事件的下发顺序 */
    private final ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-dispatch");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatTask;

    /** 最近一次 publish 的目标用户 ID；包级可见，供同包测试断言使用 */
    volatile List<Long> lastRecipientUserIds = List.of();

    /**
     * 测试用无参构造：使用默认配置。
     */
    public RealtimeStreamServiceImpl() {
        this(new RealtimeProperties(), null);
    }

    @Autowired
    public RealtimeStreamServiceImpl(RealtimeProperties properties, ObjectProvider<MeterRegistry> registryProvider) {
        this.properties = properties;
        MeterRegistry registry = registryProvider == null ? null : registryProvider.getIfAvailable();
        if (registry != null) {
            // 订阅数回落是「无 emitter 泄漏」的判据；同时供多副本阶段观测连接分布
            Gauge.builder("realtime.sse.subscribers", this, RealtimeStreamServiceImpl::subscriberCount)
                    .description("当前 SSE 订阅连接数（含同一用户的多标签/多端连接）")
                    .register(registry);
        }
    }

    /**
     * 订阅者（T-36 起带主题集合）。
     *
     * @param topics 订阅的事件名；为空集合表示**订阅全部主题**，
     *               这样 {@code subscribe(userId, admin)} 的既有行为与改造前等价
     */
    private record Subscriber(Long userId, boolean admin, Set<String> topics, SseEmitter emitter) {
        boolean accepts(String eventName) {
            return topics == null || topics.isEmpty() || topics.contains(eventName);
        }
    }

    @Override
    public SseEmitter subscribe(Long userId, boolean admin) {
        return subscribe(userId, admin, Set.of());
    }

    @Override
    public SseEmitter subscribe(Long userId, boolean admin, Set<String> topics) {
        SseEmitter emitter = new SseEmitter(properties.getStreamTimeoutMs());
        Subscriber subscriber = new Subscriber(userId, admin,
                topics == null ? Set.of() : Set.copyOf(topics), emitter);
        emitter.onCompletion(() -> removeQuietly(subscriber));
        emitter.onTimeout(() -> removeQuietly(subscriber));
        emitter.onError(e -> removeQuietly(subscriber));
        subscribers.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(subscriber);
        startHeartbeatIfNeeded();
        log.debug("SSE 订阅建立: userId={}, admin={}, 当前订阅数={}", userId, admin, subscriberCount());
        return emitter;
    }

    @Override
    public void publish(Device device, String topic, String payload) {
        publishLocal(device.getId(), device.getDeviceKey(), device.getOwnerId(), topic, payload);
    }

    @Override
    public void publishLocal(Long deviceId, String deviceKey, Long ownerId, String topic, String payload) {
        List<Subscriber> targets = snapshot().stream()
                // 管理员接收全部；普通用户仅接收自己名下设备（无归属设备只推给管理员）
                .filter(subscriber -> subscriber.admin()
                        || (ownerId != null && ownerId.equals(subscriber.userId())))
                .toList();
        lastRecipientUserIds = targets.stream().map(Subscriber::userId).toList();
        if (targets.isEmpty()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("deviceId", deviceId);
        event.put("deviceKey", deviceKey);
        event.put("topic", topic);
        event.put("payload", payload);
        dispatchExecutor.execute(() -> deliver(targets, EVENT_DATA, event));
    }

    @Override
    public void publishEvent(Long ownerId, String eventName, Map<String, Object> payload) {
        if (eventName == null || eventName.isBlank()) {
            return;
        }
        List<Subscriber> targets = snapshot().stream()
                // 与 publishLocal 同一套过滤：管理员全收，普通用户仅收自己名下，无归属只推管理员
                .filter(subscriber -> subscriber.admin()
                        || (ownerId != null && ownerId.equals(subscriber.userId())))
                .toList();
        // 事件名为空时连订阅者都不筛，直接返回：连「谁会收到」都算不出来
        if (targets.isEmpty()) {
            return;
        }
        lastRecipientUserIds = targets.stream().map(Subscriber::userId).toList();
        Map<String, Object> event = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        dispatchExecutor.execute(() -> deliver(targets, eventName, event));
    }

    private void deliver(List<Subscriber> targets, String eventName, Map<String, Object> event) {
        List<Subscriber> dead = new ArrayList<>();
        for (Subscriber subscriber : targets) {
            if (!subscriber.accepts(eventName)) {
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event().name(eventName).data(event));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 推送失败，摘除订阅者: userId={}, event={}", subscriber.userId(), eventName);
                dead.add(subscriber);
            }
        }
        dead.forEach(this::removeQuietly);
    }

    /**
     * 下发一轮心跳（SSE 注释帧）。无订阅者时停掉定时任务，避免空转。
     *
     * @return 本轮成功发送的心跳帧数
     */
    int heartbeat() {
        List<Subscriber> targets = snapshot();
        if (targets.isEmpty()) {
            stopHeartbeat();
            return 0;
        }
        List<Subscriber> dead = new ArrayList<>();
        int sent = 0;
        for (Subscriber subscriber : targets) {
            try {
                subscriber.emitter().send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
                sent++;
            } catch (IOException | IllegalStateException e) {
                dead.add(subscriber);
            }
        }
        dead.forEach(this::removeQuietly);
        return sent;
    }

    private synchronized void startHeartbeatIfNeeded() {
        long interval = properties.getHeartbeatIntervalMs();
        if (interval <= 0) {
            return;
        }
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
        }
        if (heartbeatTask == null || heartbeatTask.isDone()) {
            heartbeatTask = heartbeatExecutor.scheduleWithFixedDelay(
                    this::heartbeat, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private List<Subscriber> snapshot() {
        List<Subscriber> all = new ArrayList<>();
        subscribers.values().forEach(all::addAll);
        return all;
    }

    private void removeQuietly(Subscriber subscriber) {
        subscribers.computeIfPresent(subscriber.userId(), (userId, connections) -> {
            connections.remove(subscriber);
            return connections.isEmpty() ? null : connections;
        });
        log.debug("SSE 订阅移除: userId={}, 当前订阅数={}", subscriber.userId(), subscriberCount());
    }

    /** 当前订阅数；包级可见，供同包测试断言使用（指标 Gauge 也读它）。 */
    int subscriberCount() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    @PreDestroy
    public void shutdown() {
        stopHeartbeat();
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        dispatchExecutor.shutdownNow();
        log.info("SSE 实时通道已关闭");
    }

    int debugSubscriberCount() {
        return subscriberCount();
    }

    List<Long> debugRecipientUserIds() {
        return List.copyOf(lastRecipientUserIds);
    }

    int debugHeartbeatOnce() {
        return heartbeat();
    }
}