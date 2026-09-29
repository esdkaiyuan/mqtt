package com.mqtt.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.entity.Device;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 实时数据广播出口。
 * <p>
 * 多副本部署时把设备消息经 Redis Pub/Sub 广播到所有副本，各副本再本地扇出给自己持有的 SSE 连接；
 * 单副本（{@code broadcast-enabled=false}）时直接本地扇出，不经过 Redis。
 * 广播失败不阻断摄取链路：退化为本副本推送并记 ERROR 与指标，历史数据仍以 DB 为准。
 */
@Slf4j
@Component
public class RealtimeBroadcaster {

    private final RealtimeProperties properties;
    private final RealtimeStreamService realtimeStreamService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter published;
    private final Counter publishFailures;

    public RealtimeBroadcaster(RealtimeProperties properties,
                               RealtimeStreamService realtimeStreamService,
                               @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                               ObjectMapper objectMapper,
                               MeterRegistry registry) {
        this.properties = properties;
        this.realtimeStreamService = realtimeStreamService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.published = Counter.builder("realtime.broadcast.published")
                .description("经 Redis 频道发布的实时广播消息数")
                .register(registry);
        this.publishFailures = Counter.builder("realtime.broadcast.publish.failures")
                .description("实时广播发布失败次数")
                .register(registry);
    }

    /** 频道消息体；订阅方需要 ownerId 做归属过滤，故随消息一并传递。 */
    public record BroadcastMessage(Long deviceId, String deviceKey, Long ownerId, String topic, String payload, long ts) {
    }

    public void broadcast(Device device, String topic, String payload) {
        if (!properties.isBroadcastEnabled()) {
            publishLocal(device, topic, payload);
            return;
        }
        try {
            BroadcastMessage message = new BroadcastMessage(device.getId(), device.getDeviceKey(),
                    device.getOwnerId(), topic, payload, System.currentTimeMillis());
            redisTemplate.convertAndSend(properties.getBroadcastChannel(), objectMapper.writeValueAsString(message));
            published.increment();
        } catch (Exception e) {
            publishFailures.increment();
            log.error("实时广播发布失败，退化为本副本推送: deviceKey={}", device.getDeviceKey(), e);
            publishLocal(device, topic, payload);
        }
    }

    private void publishLocal(Device device, String topic, String payload) {
        realtimeStreamService.publishLocal(device.getId(), device.getDeviceKey(), device.getOwnerId(), topic, payload);
    }
}