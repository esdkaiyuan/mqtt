package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.service.SceneTimerSweeperService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 场景定时触发巡检实现（T-23 设计文档 §8.4）。
 * <p>
 * 逐场景隔离异常；单场景流程：{@code matchesTimerTrigger}（cron 命中当前分钟，按 {@code timer-zone}）
 * → {@code touchLastTriggeredOnce}（**数据库级分钟去重**，返回 {@code 0} 说明本分钟已被其他副本触发）
 * → 冷却判定（{@link SceneCooldownRegistry}）→ 条件组判定（{@link SceneMatcher}，{@code TIMER} 源条件项
 * 设备必填）→ {@link SceneExecutionLauncher} 落执行记录 + 投递（{@code trigger_source=AUTO}）。
 * <p>
 * 分钟锚点（{@code minuteFloor}）与 cron 判定**使用同一时区**，避免跨时区时去重口径与触发口径不一致。
 */
@Slf4j
@Service
public class SceneTimerSweeperServiceImpl implements SceneTimerSweeperService {

    private final SceneDefinitionMapper definitionMapper;
    private final SceneMatcher sceneMatcher;
    private final SceneCooldownRegistry cooldownRegistry;
    private final SceneExecutionLauncher launcher;
    private final SceneProperties properties;

    private final Counter triggerCounter;

    public SceneTimerSweeperServiceImpl(SceneDefinitionMapper definitionMapper,
                                        SceneMatcher sceneMatcher,
                                        SceneCooldownRegistry cooldownRegistry,
                                        SceneExecutionLauncher launcher,
                                        SceneProperties properties,
                                        MeterRegistry meterRegistry) {
        this.definitionMapper = definitionMapper;
        this.sceneMatcher = sceneMatcher;
        this.cooldownRegistry = cooldownRegistry;
        this.launcher = launcher;
        this.properties = properties;
        this.triggerCounter = Counter.builder("scene_timer_trigger_total")
                .description("定时场景命中并落执行记录次数")
                .register(meterRegistry);
    }

    @Override
    public int sweepTimers() {
        if (!properties.isEnabled()) {
            return 0;
        }
        List<SceneDefinition> scenes = definitionMapper.selectEnabledTimers();
        if (scenes == null || scenes.isEmpty()) {
            return 0;
        }
        ZoneId zone = sceneMatcher.timerZone();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime minuteFloor = now.truncatedTo(ChronoUnit.MINUTES);
        int fired = 0;
        for (SceneDefinition scene : scenes) {
            try {
                if (fire(scene, now, minuteFloor)) {
                    fired++;
                }
            } catch (Exception e) {
                log.warn("定时场景触发异常，跳过: sceneId={}", scene.getId(), e);
            }
        }
        return fired;
    }

    /**
     * 单场景定时触发：命中 → 去重 → 冷却 → 条件组 → 落库投递。
     *
     * @return 是否实际触发
     */
    private boolean fire(SceneDefinition scene, LocalDateTime now, LocalDateTime minuteFloor) {
        if (!sceneMatcher.matchesTimerTrigger(scene, now)) {
            return false;
        }
        // 数据库级分钟去重：返回 0 表示本分钟已被其他副本 / 其他巡检轮次触发
        if (definitionMapper.touchLastTriggeredOnce(scene.getId(), now, minuteFloor) != 1) {
            return false;
        }
        if (!cooldownRegistry.acquire(scene)) {
            log.debug("定时场景冷却窗口内，跳过: sceneId={}", scene.getId());
            return false;
        }
        boolean satisfied = sceneMatcher.conditionsSatisfied(
                sceneMatcher.evaluateConditions(scene, null), scene.getConditionLogic());
        if (!satisfied) {
            log.debug("定时场景条件组不满足，跳过: sceneId={}", scene.getId());
            return false;
        }
        try {
            launcher.launch(scene, SceneExecutionLauncher.TriggerContext.empty(), SceneConstants.SOURCE_AUTO);
            triggerCounter.increment();
            return true;
        } catch (Exception e) {
            log.warn("定时场景执行记录落库失败，跳过本次触发: sceneId={}", scene.getId(), e);
            return false;
        }
    }
}