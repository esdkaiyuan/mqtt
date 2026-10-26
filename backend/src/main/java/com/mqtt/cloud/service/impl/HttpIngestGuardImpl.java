package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.HttpIngestProperties;
import com.mqtt.cloud.service.HttpIngestGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

/**
 * HTTP 上报守卫实现（T-53）。
 * <p>
 * <b>限流</b>：按设备固定窗口计数（默认 60 秒），键为
 * {@code prefix + deviceKey + ":" + 窗口序号}，首次计数时设置过期，窗口自然回收。
 * 默认关闭——与 {@code app.rate-limit} / 命令配额同一降级语义，未显式开启不会打断正常设备。
 * <p>
 * <b>幂等</b>：设备在 {@code Idempotency-Key} 中携带请求唯一标识（如设备侧递增序号 + 启动批次），
 * 平台以 {@code SET NX EX} 原子写入；已存在即判定重复。键值存首次受理时间，重复响应可原样返回。
 * <p>
 * <b>Redis 故障策略</b>：限流按 {@code rateLimitFailOpen} 决定放行或拒绝；幂等失败一律放行
 * （宁可重复一条数据，也不能让 Redis 故障阻断全部设备上报）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HttpIngestGuardImpl implements HttpIngestGuard {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final long RATE_WINDOW_SECONDS = 60L;

    private final RedisTemplate<String, String> redisTemplate;
    private final HttpIngestProperties properties;

    @Override
    public GuardResult check(String deviceKey, String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        if (properties.isRateLimitEnabled()) {
            checkRate(deviceKey);
        }
        String normalized = normalizeIdempotencyKey(idempotencyKey);
        if (normalized == null) {
            return new GuardResult(false, now);
        }
        return checkIdempotency(deviceKey, normalized, now);
    }

    private void checkRate(String deviceKey) {
        long window = System.currentTimeMillis() / 1000L / RATE_WINDOW_SECONDS;
        String key = properties.getRateLimitKeyPrefix() + deviceKey + ":" + window;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(RATE_WINDOW_SECONDS));
            }
            if (count != null && count > properties.getRateLimitPerMinute()) {
                log.warn("HTTP 上报触发限流 deviceKey={} count={} limit={}",
                        deviceKey, count, properties.getRateLimitPerMinute());
                throw new BusinessException(ResultCode.INGEST_RATE_LIMIT_EXCEEDED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (!properties.isRateLimitFailOpen()) {
                log.error("HTTP 上报限流检查失败且 failOpen=false，拒绝请求 deviceKey={}", deviceKey, e);
                throw new BusinessException(ResultCode.INGEST_RATE_LIMIT_EXCEEDED);
            }
            log.warn("HTTP 上报限流检查失败，降级放行 deviceKey={} err={}", deviceKey, e.getMessage());
        }
    }

    private GuardResult checkIdempotency(String deviceKey, String idempotencyKey, LocalDateTime now) {
        String key = properties.getIdempotencyKeyPrefix() + deviceKey + ":" + sha256(idempotencyKey);
        int ttl = Math.max(1, properties.getIdempotencyTtlSeconds());
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(key, now.toString(), Duration.ofSeconds(ttl));
            if (!Boolean.FALSE.equals(first)) {
                // true 或 null（Redis 未明确返回）都按首次受理处理，避免把正常请求误判为重复
                return new GuardResult(false, now);
            }
            String stored = redisTemplate.opsForValue().get(key);
            return new GuardResult(true, parseStoredTime(stored, now));
        } catch (Exception e) {
            // 幂等 Redis 故障：放行，避免把设备上报整体打死
            log.warn("HTTP 幂等检查失败，降级放行 deviceKey={} err={}", deviceKey, e.getMessage());
            return new GuardResult(false, now);
        }
    }

    /**
     * 幂等键校验：只允许可见 ASCII，避免把控制字符 / 超长内容写进 Redis key。
     */
    private String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ResultCode.INGEST_IDEMPOTENCY_KEY_INVALID);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                throw new BusinessException(ResultCode.INGEST_IDEMPOTENCY_KEY_INVALID);
            }
        }
        return value;
    }

    private LocalDateTime parseStoredTime(String stored, LocalDateTime fallback) {
        if (stored == null || stored.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(stored);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走不到这里
            return Integer.toHexString(value.hashCode());
        }
    }
}