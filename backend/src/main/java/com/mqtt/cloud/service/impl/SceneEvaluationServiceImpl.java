package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.constant.ShadowChangeSide;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.SceneEvaluationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景触发评估实现（T-23 设计文档 §8.1 / §8.2 / §8.3 / §8.4）。
 * <p>
 * 场景定义按用户短 TTL 缓存（{@code app.scene.cache-ttl-seconds}，{@code 0} 关闭），写操作后由
 * {@link SceneServiceImpl} 主动失效本副本。匹配顺序：来源 → 作用域 → 标识符（事件源额外比对事件类型）
 * → 触发条件 → 条件组。
 * <p>
 * 命中后：冷却判定（{@link SceneCooldownRegistry}，先占用再执行）→ {@link SceneExecutionLauncher}
 * 落执行记录 + 初始化步骤流 + 投递。落库失败只记 WARN + 指标，不冒泡；这是场景引擎在摄取线程上的
 * **唯一一次落库事务**。
 * <p>
 * 条件组判定与触发匹配复用 {@link SceneMatcher}，与试运行 / 定时巡检同一份口径。
 */
@Slf4j
@Service
public class SceneEvaluationServiceImpl implements SceneEvaluationService {

    private final SceneDefinitionMapper sceneDefinitionMapper;
    private final DeviceMapper deviceMapper;
    private final SceneMatcher sceneMatcher;
    private final SceneCooldownRegistry cooldownRegistry;
    private final SceneExecutionLauncher launcher;
    private final SceneProperties properties;

    private final Counter triggerCounter;

    /** userId → 启用场景列表缓存；值为不可变列表快照，不承载执行态。 */
    private final ConcurrentHashMap<Long, CachedScenes> cache = new ConcurrentHashMap<>();

    public SceneEvaluationServiceImpl(SceneDefinitionMapper sceneDefinitionMapper,
                                      DeviceMapper deviceMapper,
                                      SceneMatcher sceneMatcher,
                                      SceneCooldownRegistry cooldownRegistry,
                                      SceneExecutionLauncher launcher,
                                      SceneProperties properties,
                                      MeterRegistry meterRegistry) {
        this.sceneDefinitionMapper = sceneDefinitionMapper;
        this.deviceMapper = deviceMapper;
        this.sceneMatcher = sceneMatcher;
        this.cooldownRegistry = cooldownRegistry;
        this.launcher = launcher;
        this.properties = properties;
        this.triggerCounter = Counter.builder("scene_trigger_total")
                .description("场景命中并落执行记录次数")
                .register(meterRegistry);
    }

    @Override
    public void onProperties(Long deviceId, Long productId, List<PropertySample> samples) {
        if (!properties.isEnabled() || deviceId == null || samples == null || samples.isEmpty()) {
            return;
        }
        try {
            Device device = loadDevice(deviceId);
            if (device == null) {
                return;
            }
            List<SceneDefinition> scenes = loadEnabledScenes(device.getOwnerId());
            for (SceneDefinition scene : scenes) {
                if (!SceneConstants.TRIGGER_PROPERTY.equals(scene.getTriggerType())) {
                    continue;
                }
                for (PropertySample sample : samples) {
                    if (!sceneMatcher.matchesPropertyTrigger(
                            scene, deviceId, sample.identifier(), sample.valueText())) {
                        continue;
                    }
                    fire(scene, device, new SceneExecutionLauncher.TriggerContext(
                            deviceId, device.getDeviceKey(), device.getDeviceName(),
                            sample.identifier(), sample.valueText(), null, sample.reportedAt()));
                }
            }
        } catch (Exception e) {
            log.warn("场景属性触发评估异常，跳过: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void onEvents(Long deviceId, List<EventSample> samples) {
        if (!properties.isEnabled() || deviceId == null || samples == null || samples.isEmpty()) {
            return;
        }
        try {
            Device device = loadDevice(deviceId);
            if (device == null) {
                return;
            }
            List<SceneDefinition> scenes = loadEnabledScenes(device.getOwnerId());
            for (SceneDefinition scene : scenes) {
                if (!SceneConstants.TRIGGER_EVENT.equals(scene.getTriggerType())) {
                    continue;
                }
                for (EventSample sample : samples) {
                    if (!sceneMatcher.matchesEventTrigger(
                            scene, deviceId, sample.identifier(), sample.eventType())) {
                        continue;
                    }
                    fire(scene, device, new SceneExecutionLauncher.TriggerContext(
                            deviceId, device.getDeviceKey(), device.getDeviceName(),
                            sample.identifier(), sample.outputData(), sample.eventType(), sample.reportedAt()));
                }
            }
        } catch (Exception e) {
            log.warn("场景事件触发评估异常，跳过: deviceId={}", deviceId, e);
        }
    }

    @Override
    // AFTER_COMMIT 回调发生在连接清理之前，REQUIRED 会误加入已提交事务导致执行记录不落库；
    // 用 REQUIRES_NEW 为场景触发开一个独立事务（T-43）
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onStatus(Long deviceId, String status, LocalDateTime occurredAt) {
        if (!properties.isEnabled() || deviceId == null || status == null) {
            return;
        }
        try {
            Device device = loadDevice(deviceId);
            if (device == null) {
                return;
            }
            List<SceneDefinition> scenes = loadEnabledScenes(device.getOwnerId());
            LocalDateTime triggeredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
            for (SceneDefinition scene : scenes) {
                if (!sceneMatcher.matchesStatusTrigger(scene, deviceId, status)) {
                    continue;
                }
                fire(scene, device, new SceneExecutionLauncher.TriggerContext(
                        deviceId, device.getDeviceKey(), device.getDeviceName(),
                        null, status, null, triggeredAt));
            }
        } catch (Exception e) {
            log.warn("场景上下线触发评估异常，跳过: deviceId={}, status={}", deviceId, status, e);
        }
    }

    @Override
    // 与 onStatus 同理：AFTER_COMMIT 回调中必须使用独立事务
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onShadow(Long deviceId, String side, Map<String, String> changes,
                         String source, LocalDateTime occurredAt) {
        if (!properties.isEnabled() || deviceId == null || side == null
                || changes == null || changes.isEmpty()) {
            return;
        }
        if (internalDesiredSource(side, source)) {
            log.debug("影子 desired 变更来自内部自动化，跳过场景触发: deviceId={}, source={}", deviceId, source);
            return;
        }
        try {
            Device device = loadDevice(deviceId);
            if (device == null) {
                return;
            }
            List<SceneDefinition> scenes = loadEnabledScenes(device.getOwnerId());
            LocalDateTime triggeredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
            String eventType = side.toLowerCase(Locale.ROOT);
            String value = changesText(changes);
            for (SceneDefinition scene : scenes) {
                if (!SceneConstants.TRIGGER_SHADOW.equals(scene.getTriggerType())
                        || !sceneMatcher.matchesShadowTrigger(scene, deviceId, side, changes.keySet())) {
                    continue;
                }
                String identifier = scene.getTriggerIdentifier() != null
                        ? scene.getTriggerIdentifier() : changes.keySet().iterator().next();
                fire(scene, device, new SceneExecutionLauncher.TriggerContext(
                        deviceId, device.getDeviceKey(), device.getDeviceName(),
                        identifier, value, eventType, triggeredAt));
            }
        } catch (Exception e) {
            log.warn("场景影子变更触发评估异常，跳过: deviceId={}, side={}", deviceId, side, e);
        }
    }

    /** desired 变更仅外部写入（控制台 / 开放 API / 直接调用）触发场景，内部自动化跳过。 */
    static boolean internalDesiredSource(String side, String source) {
        return ShadowChangeSide.DESIRED.equalsIgnoreCase(side)
                && source != null
                && !DeviceCommandService.SOURCE_CONSOLE.equals(source)
                && !DeviceCommandService.SOURCE_OPEN_API.equals(source);
    }

    private static String changesText(Map<String, String> changes) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : changes.entrySet()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.toString();
    }

    @Override
    public void evictCache(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /**
     * 命中处理：冷却判定（先占用）→ 条件组判定 → 落执行记录 + 投递。
     * <p>
     * 条件组不满足 / 冷却窗口内重复命中直接跳过（记 DEBUG，不落记录，不占冷却锚点）。
     */
    private void fire(SceneDefinition scene, Device device, SceneExecutionLauncher.TriggerContext context) {
        if (!cooldownRegistry.acquire(scene)) {
            log.debug("场景冷却窗口内，跳过: sceneId={}", scene.getId());
            return;
        }
        boolean satisfied = sceneMatcher.conditionsSatisfied(
                sceneMatcher.evaluateConditions(scene, device.getId()), scene.getConditionLogic());
        if (!satisfied) {
            log.debug("场景条件组不满足，跳过: sceneId={}", scene.getId());
            return;
        }
        try {
            launcher.launch(scene, context, SceneConstants.SOURCE_AUTO);
            triggerCounter.increment();
        } catch (Exception e) {
            log.warn("场景执行记录落库失败，跳过本次触发: sceneId={}, deviceId={}",
                    scene.getId(), device.getId(), e);
        }
    }

    /**
     * 加载某用户的启用场景（走短 TTL 缓存）。
     * <p>
     * 供触发评估使用；TTL 为 {@code 0} 时每次回源查库。
     */
    public List<SceneDefinition> loadEnabledScenes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        int ttlSeconds = properties.getCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return sceneDefinitionMapper.selectEnabledByUser(userId);
        }
        long now = System.currentTimeMillis();
        CachedScenes cached = cache.get(userId);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.scenes();
        }
        List<SceneDefinition> scenes = sceneDefinitionMapper.selectEnabledByUser(userId);
        cache.put(userId, new CachedScenes(List.copyOf(scenes), now + ttlSeconds * 1000L));
        return scenes;
    }

    private Device loadDevice(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || device.getOwnerId() == null) {
            return null;
        }
        return device;
    }

    /** 缓存条目：启用场景列表快照 + 过期时刻（毫秒）。 */
    private record CachedScenes(List<SceneDefinition> scenes, long expiresAtMillis) {
    }
}