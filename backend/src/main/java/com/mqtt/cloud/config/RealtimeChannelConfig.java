package com.mqtt.cloud.config;

import com.mqtt.cloud.service.RealtimeChannelSubscriber;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 跨副本实时广播的 Redis 监听容器。
 * <p>
 * 仅在 {@code app.realtime.broadcast-enabled=true} 时创建；单副本模式不建立任何 Redis 订阅，
 * 避免无谓的连接与告警。Redis 断连时容器会持续重连，此处记录 ERROR 并计数，保证「故障可告警而非静默」。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.realtime", name = "broadcast-enabled", havingValue = "true")
public class RealtimeChannelConfig {

    @Bean
    public RedisMessageListenerContainer realtimeChannelListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeChannelSubscriber subscriber,
            RealtimeProperties properties,
            MeterRegistry registry) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(properties.getBroadcastChannel()));

        Counter connectionErrors = Counter.builder("realtime.broadcast.connection.errors")
                .description("Redis 实时广播监听连接异常次数")
                .register(registry);
        container.setErrorHandler(throwable -> {
            connectionErrors.increment();
            log.error("Redis 实时广播监听异常，跨副本实时链路可能已降级（Redis 断连或超时）", throwable);
        });

        log.info("跨副本实时广播已启用: channel={}", properties.getBroadcastChannel());
        return container;
    }
}