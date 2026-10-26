package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.ThingModelProperties;
import com.mqtt.cloud.service.PayloadCodecDefinition;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Payload Codec 定义进程内缓存（T-55）。
 * <p>
 * 与 {@link ThingModelCache} 同款：TTL + 主动失效，多副本收敛窗口 = TTL。
 * 缓存「未配置」的空定义，避免每条上行都对无 Codec 产品回源查库。
 */
@Component
public class PayloadCodecCache {

    private final ThingModelProperties properties;
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();

    public PayloadCodecCache(ThingModelProperties properties) {
        this.properties = properties;
    }

    public PayloadCodecDefinition get(Long productId) {
        if (!enabled()) {
            return null;
        }
        Entry entry = entries.get(productId);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiresAtMillis()) {
            entries.remove(productId, entry);
            return null;
        }
        return entry.definition();
    }

    public void put(Long productId, PayloadCodecDefinition definition) {
        if (!enabled() || definition == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + properties.getCacheTtlSeconds() * 1000L;
        entries.put(productId, new Entry(definition, expiresAt));
    }

    public void evict(Long productId) {
        if (productId != null) {
            entries.remove(productId);
        }
    }

    private boolean enabled() {
        return properties.getCacheTtlSeconds() > 0;
    }

    private record Entry(PayloadCodecDefinition definition, long expiresAtMillis) {
    }
}