package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.entity.SceneDefinition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景触发冷却锚点（T-23 设计文档 §8.4）。
 * <p>
 * 锚点为**进程内** {@code ConcurrentHashMap<Long, Instant>}（场景 ID → 上次触发时刻），
 * 摄取线程**零额外读写库**；多副本下同一窗口内最多触发 N 次（N = 副本数），属可接受的节流近似。
 * <p>
 * 供 {@code SceneEvaluationServiceImpl}（上行触发）与 {@code SceneTimerSweeperServiceImpl}（定时触发）
 * 共用；手动执行（{@code runManually}）**不占用锚点**（用户显式触发，不受冷却约束）。
 */
@Component
public class SceneCooldownRegistry {

    private final ConcurrentHashMap<Long, Instant> cooldowns = new ConcurrentHashMap<>();

    /**
     * 冷却判定并**占用锚点**（先占用再执行，避免同一批内连续命中重复触发）。
     *
     * @return {@code true} 允许触发（并已占用锚点）；{@code false} 冷却窗口内应跳过
     */
    public boolean acquire(SceneDefinition scene) {
        int cooldownSeconds = scene.getCooldownSeconds() == null ? 0 : scene.getCooldownSeconds();
        if (cooldownSeconds <= 0) {
            return true;
        }
        Instant now = Instant.now();
        boolean[] allowed = {false};
        cooldowns.compute(scene.getId(), (id, last) -> {
            if (last == null || Duration.between(last, now).getSeconds() >= cooldownSeconds) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }

    /** 只读判定：当前是否处于冷却窗口内（试运行干跑用，**不占用锚点**）。 */
    public boolean isBlocked(Long sceneId, int cooldownSeconds) {
        if (sceneId == null || cooldownSeconds <= 0) {
            return false;
        }
        Instant last = cooldowns.get(sceneId);
        return last != null && Duration.between(last, Instant.now()).getSeconds() < cooldownSeconds;
    }

    /** 场景删除后清理锚点（避免 ID 复用带来的误判）。 */
    public void evict(Long sceneId) {
        if (sceneId != null) {
            cooldowns.remove(sceneId);
        }
    }
}