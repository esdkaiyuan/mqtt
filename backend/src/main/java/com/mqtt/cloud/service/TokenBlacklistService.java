package com.mqtt.cloud.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务
 * <p>
 * 登出后的 Token 在过期前写入 Redis 黑名单，认证过滤器据此拒绝已登出的 Token。
 * Redis 不可用时按"未拉黑"处理并告警，避免缓存故障导致全站无法登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public void add(String token, long ttlSeconds) {
        if (token == null || token.isBlank() || ttlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Token 写入黑名单失败: {}", e.getMessage());
        }
    }

    public boolean contains(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
        } catch (Exception e) {
            log.warn("查询 Token 黑名单失败，按未拉黑处理: {}", e.getMessage());
            return false;
        }
    }
}