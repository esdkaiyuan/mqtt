package com.mqtt.cloud.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Set;

/**
 * 基于 Redis 的认证元数据缓存实现。
 * <p>
 * 读写失败一律降级为「未命中」而非抛出：缓存是性能优化，不能成为认证可用性的单点。
 * 密钥轮换等安全语义由主动失效 + 短 TTL 共同保证。
 */
@Slf4j
@Service
public class DeviceAuthCacheServiceImpl implements DeviceAuthCacheService {

    private static final String KEY_PREFIX = "auth:meta:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AccessControlProperties properties;

    public DeviceAuthCacheServiceImpl(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                                      ObjectMapper objectMapper,
                                      AccessControlProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AuthMeta get(String productKey, String deviceKey) {
        if (!cacheEnabled()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(key(productKey, deviceKey));
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, AuthMeta.class);
        } catch (Exception e) {
            log.warn("读取认证缓存失败，降级查库: productKey={}, deviceKey={}", productKey, deviceKey, e);
            return null;
        }
    }

    @Override
    public void put(String productKey, String deviceKey, AuthMeta meta) {
        if (!cacheEnabled() || meta == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(productKey, deviceKey),
                    objectMapper.writeValueAsString(meta),
                    Duration.ofSeconds(properties.getCacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("写入认证缓存失败，不影响本次认证: productKey={}, deviceKey={}", productKey, deviceKey, e);
        }
    }

    @Override
    public void evict(String productKey, String deviceKey) {
        try {
            redisTemplate.delete(key(productKey, deviceKey));
        } catch (Exception e) {
            log.warn("失效认证缓存失败，将依赖 TTL 自然过期: productKey={}, deviceKey={}", productKey, deviceKey, e);
        }
    }

    @Override
    public void evictProduct(String productKey) {
        if (!StringUtils.hasText(productKey)) {
            return;
        }
        try {
            // 产品级操作为低频管理动作，用 KEYS 精确匹配前缀即可；认证热路径不涉及此调用
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + productKey + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("按产品失效认证缓存失败，将依赖 TTL 自然过期: productKey={}", productKey, e);
        }
    }

    private boolean cacheEnabled() {
        return properties.getCacheTtlSeconds() > 0;
    }

    private String key(String productKey, String deviceKey) {
        return KEY_PREFIX + productKey + ":" + deviceKey;
    }
}