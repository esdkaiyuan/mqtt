package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.HttpIngestProperties;
import com.mqtt.cloud.service.HttpIngestGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link HttpIngestGuardImpl} 单元测试（T-53）。
 */
class HttpIngestGuardImplTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;
    private HttpIngestProperties properties;
    private HttpIngestGuardImpl guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        properties = new HttpIngestProperties();
        guard = new HttpIngestGuardImpl(redisTemplate, properties);
    }

    @Test
    void withoutIdempotencyKey_andRateLimitDisabled_should_notTouchRedis() {
        HttpIngestGuard.GuardResult result = guard.check("dev-1", null);

        assertThat(result.duplicate()).isFalse();
        verifyNoInteractions(valueOps);
    }

    @Test
    void firstIdempotencyKey_should_notBeDuplicate() {
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);

        HttpIngestGuard.GuardResult result = guard.check("dev-1", "req-1");

        assertThat(result.duplicate()).isFalse();
        verify(valueOps).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void repeatedIdempotencyKey_should_returnDuplicate_andFirstAcceptedAt() {
        LocalDateTime firstAt = LocalDateTime.now().minusSeconds(30);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);
        given(valueOps.get(anyString())).willReturn(firstAt.toString());

        HttpIngestGuard.GuardResult result = guard.check("dev-1", "req-1");

        assertThat(result.duplicate()).isTrue();
        assertThat(result.firstAcceptedAt()).isEqualTo(firstAt);
    }

    @Test
    void invalidIdempotencyKey_should_throw6315_withoutRedis() {
        assertThatThrownBy(() -> guard.check("dev-1", "bad\nkey"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6315));

        verifyNoInteractions(valueOps);
    }

    @Test
    void rateLimitExceeded_should_throw6314() {
        properties.setRateLimitEnabled(true);
        given(valueOps.increment(anyString())).willReturn(601L);

        assertThatThrownBy(() -> guard.check("dev-1", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6314));
    }

    @Test
    void rateLimitFirstHit_should_setExpiry_andAllow() {
        properties.setRateLimitEnabled(true);
        given(valueOps.increment(anyString())).willReturn(1L);

        HttpIngestGuard.GuardResult result = guard.check("dev-1", null);

        assertThat(result.duplicate()).isFalse();
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    void rateLimitRedisFailure_failOpenTrue_shouldAllow() {
        properties.setRateLimitEnabled(true);
        given(valueOps.increment(anyString())).willThrow(new RuntimeException("redis down"));

        assertThat(guard.check("dev-1", null).duplicate()).isFalse();
    }

    @Test
    void rateLimitRedisFailure_failOpenFalse_shouldReject() {
        properties.setRateLimitEnabled(true);
        properties.setRateLimitFailOpen(false);
        given(valueOps.increment(anyString())).willThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> guard.check("dev-1", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6314));
    }

    @Test
    void idempotencyRedisFailure_shouldFailOpen() {
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willThrow(new RuntimeException("redis down"));

        HttpIngestGuard.GuardResult result = guard.check("dev-1", "req-1");

        assertThat(result.duplicate()).isFalse();
        verify(valueOps, never()).get(anyString());
    }
}