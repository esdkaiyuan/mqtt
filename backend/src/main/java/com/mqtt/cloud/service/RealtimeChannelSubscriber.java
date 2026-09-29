package com.mqtt.cloud.service;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis 广播订阅者：把跨副本广播的消息在本副本内扇出给 SSE 订阅者。
 * <p>
 * 归属过滤由 {@link RealtimeStreamService#publishLocal} 完成，故必须原样透传 {@code ownerId}。
 * 非法消息只记 WARN 并计数，绝不让异常冒泡到监听容器，否则会反复中断订阅连接。
 */
@Slf4j
@Component
public class RealtimeChannelSubscriber implements MessageListener {

    private final RealtimeStreamService realtimeStreamService;
    private final ObjectMapper objectMapper;
    private final Counter received;
    private final Counter parseFailures;

    public RealtimeChannelSubscriber(RealtimeStreamService realtimeStreamService,
                                     ObjectMapper objectMapper,
                                     MeterRegistry registry) {
        this.realtimeStreamService = realtimeStreamService;
        this.objectMapper = objectMapper;
        this.received = Counter.builder("realtime.broadcast.received")
                .description("从 Redis 频道接收到的实时广播消息数")
                .register(registry);
        this.parseFailures = Counter.builder("realtime.broadcast.parse.failures")
                .description("实时广播消息解析失败次数")
                .register(registry);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        received.increment();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            RealtimeBroadcaster.BroadcastMessage event =
                    objectMapper.readValue(body, RealtimeBroadcaster.BroadcastMessage.class);
            realtimeStreamService.publishLocal(event.deviceId(), event.deviceKey(), event.ownerId(),
                    event.topic(), event.payload());
        } catch (Exception e) {
            parseFailures.increment();
            log.warn("实时广播消息解析失败，已忽略: body={}", body, e);
        }
    }
}