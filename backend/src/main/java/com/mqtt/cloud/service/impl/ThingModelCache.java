package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.ThingModelProperties;
import com.mqtt.cloud.service.ThingModelDefinition;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 物模型定义的进程内缓存（TTL + 主动失效）。
 * <p>
 * 只缓存定义本身（非敏感、无权限语义）；保存物模型时由调用方<b>提交后</b>失效本副本，
 * 多副本之间的收敛窗口 = {@code app.thing-model.cache-ttl-seconds}，与 ACL 缓存同一「TTL 兜底」思路。
 */
@Component
public class ThingModelCache {

    private final ThingModelProperties properties;
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();

    public ThingModelCache(ThingModelProperties properties) {
        this.properties = properties;
    }

    /** 命中且未过期才返回，否则返回 {@code null}（调用方回源）。 */
    public ThingModelDefinition get(Long productId) {
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

    /** 缓存「未建模」的空定义，避免热路径对无物模型产品反复回源。 */
    public void put(Long productId, ThingModelDefinition definition) {
        if (!enabled() || definition == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + properties.getCacheTtlSeconds() * 1000L;
        entries.put(productId, new Entry(definition, expiresAt));
    }

    public void evict(Long productId) {
        entries.remove(productId);
    }

    private boolean enabled() {
        return properties.getCacheTtlSeconds() > 0;
    }

    private record Entry(ThingModelDefinition definition, long expiresAtMillis) {
    }
}