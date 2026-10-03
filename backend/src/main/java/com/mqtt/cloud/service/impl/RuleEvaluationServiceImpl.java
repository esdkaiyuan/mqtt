package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.RuleDefinition;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.RuleDefinitionMapper;
import com.mqtt.cloud.mapper.RuleExecutionMapper;
import com.mqtt.cloud.service.RuleActionExecutor;
import com.mqtt.cloud.service.RuleEvaluationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * 消息规则评估实现（T-19 设计文档 §8.2 / §8.4）。
 * <p>
 * 规则定义按用户短 TTL 缓存（{@code app.rule.cache-ttl-seconds}，{@code 0} 关闭），写操作后由
 * {@link RuleServiceImpl} 主动失效本副本。匹配顺序：作用域 → 来源 → 标识符（事件源额外比对事件类型）→ 条件。
 * <p>
 * 冷却锚点为**进程内** {@code ConcurrentHashMap<Long, Instant>}（规则 ID → 上次触发时刻），
 * 摄取线程**零额外读写库**；多副本下同一窗口内最多触发 N 次（N = 副本数），属可接受的节流近似。
 * <p>
 * 命中后先落 {@code rule_execution}（{@code PENDING}、{@code attempt_count=0}、{@code next_attempt_at=NULL}）
 * 再投递 {@code ruleExecutor}：落库失败宁可丢一次触发也不影响摄取链路；队列满则置 {@code FAILED}。
 * 全方法 {@code try/catch}，异常只记 WARN。
 */
@Slf4j
@Service
public class RuleEvaluationServiceImpl implements RuleEvaluationService {

    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final DeviceMapper deviceMapper;
    private final RuleExecutionMapper ruleExecutionMapper;
    private final RuleActionExecutor ruleActionExecutor;
    private final RuleProperties properties;
    private final ThreadPoolTaskExecutor ruleExecutor;

    private final Counter triggerCounter;
    private final Counter rejectedCounter;

    /** 用户 ID → 规则定义缓存（不含任何执行态）。 */
    private final ConcurrentHashMap<Long, CachedRules> cache = new ConcurrentHashMap<>();

    /** 规则 ID → 上次触发时刻（进程内冷却锚点）。 */
    private final ConcurrentHashMap<Long, Instant> cooldowns = new ConcurrentHashMap<>();

    public RuleEvaluationServiceImpl(RuleDefinitionMapper ruleDefinitionMapper,
                                     DeviceMapper deviceMapper,
                                     RuleExecutionMapper ruleExecutionMapper,
                                     RuleActionExecutor ruleActionExecutor,
                                     RuleProperties properties,
                                     @Qualifier("ruleExecutor") ThreadPoolTaskExecutor ruleExecutor,
                                     MeterRegistry meterRegistry) {
        this.ruleDefinitionMapper = ruleDefinitionMapper;
        this.deviceMapper = deviceMapper;
        this.ruleExecutionMapper = ruleExecutionMapper;
        this.ruleActionExecutor = ruleActionExecutor;
        this.properties = properties;
        this.ruleExecutor = ruleExecutor;
        this.triggerCounter = Counter.builder("rule_trigger_total")
                .description("规则命中并落执行记录次数")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("rule_rejected_total")
                .description("规则动作投递被线程池拒绝次数")
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
            List<RuleDefinition> rules = loadRules(device.getOwnerId());
            for (RuleDefinition rule : rules) {
                if (!RuleConstants.SOURCE_PROPERTY.equals(rule.getSourceType())
                        || !inScope(rule, deviceId)) {
                    continue;
                }
                for (PropertySample sample : samples) {
                    if (!identifierEquals(rule.getIdentifier(), sample.identifier())) {
                        continue;
                    }
                    Boolean matched = RuleConditionMatcher.matchesProperty(
                            sample.valueText(), rule.getOperator(), rule.getThresholdValue());
                    if (Boolean.TRUE.equals(matched)) {
                        trigger(rule, device, RuleConstants.SOURCE_PROPERTY,
                                sample.identifier(), sample.valueText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("属性规则评估异常，跳过: deviceId={}", deviceId, e);
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
            List<RuleDefinition> rules = loadRules(device.getOwnerId());
            for (RuleDefinition rule : rules) {
                if (!RuleConstants.SOURCE_EVENT.equals(rule.getSourceType())
                        || !inScope(rule, deviceId)) {
                    continue;
                }
                for (EventSample sample : samples) {
                    if (!RuleConditionMatcher.matchesEvent(rule.getIdentifier(), rule.getEventType(),
                            sample.identifier(), sample.eventType())) {
                        continue;
                    }
                    trigger(rule, device, RuleConstants.SOURCE_EVENT,
                            sample.identifier(), sample.outputData());
                }
            }
        } catch (Exception e) {
            log.warn("事件规则评估异常，跳过: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void evictCache(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /**
     * 命中处理：冷却判定 → 占用锚点 → 落执行记录 → 投递线程池。
     * <p>
     * 冷却窗口内重复命中直接跳过（记 DEBUG，不落记录）；落库或投递失败只记 WARN + 指标，不冒泡。
     */
    private void trigger(RuleDefinition rule, Device device, String sourceType,
                         String identifier, String triggerValue) {
        if (!acquireCooldown(rule)) {
            log.debug("规则冷却窗口内，跳过: ruleId={}", rule.getId());
            return;
        }
        RuleExecution execution = new RuleExecution();
        execution.setUserId(rule.getUserId());
        execution.setRuleId(rule.getId());
        execution.setRuleName(rule.getName());
        execution.setDeviceId(device.getId());
        execution.setDeviceKey(device.getDeviceKey());
        execution.setDeviceName(device.getDeviceName());
        execution.setSourceType(sourceType);
        execution.setIdentifier(identifier);
        execution.setTriggerValue(truncate(triggerValue, 512));
        execution.setActionType(rule.getActionType());
        execution.setStatus(RuleConstants.STATUS_PENDING);
        execution.setAttemptCount(0);
        execution.setNextAttemptAt(null);
        try {
            ruleExecutionMapper.insert(execution);
        } catch (Exception e) {
            log.warn("规则执行记录落库失败，跳过本次触发: ruleId={}, deviceId={}",
                    rule.getId(), device.getId(), e);
            return;
        }
        triggerCounter.increment();
        try {
            Long executionId = execution.getId();
            ruleExecutor.execute(() -> ruleActionExecutor.execute(executionId));
        } catch (RejectedExecutionException e) {
            rejectedCounter.increment();
            log.warn("规则动作投递被拒绝（执行队列已满）: executionId={}", execution.getId());
            markRejected(execution.getId());
        }
    }

    /** 冷却判定并占用锚点（先占用再执行，避免同一批内连续命中重复触发）。 */
    private boolean acquireCooldown(RuleDefinition rule) {
        int cooldownSeconds = rule.getCooldownSeconds() == null ? 0 : rule.getCooldownSeconds();
        if (cooldownSeconds <= 0) {
            return true;
        }
        Instant now = Instant.now();
        boolean[] allowed = {false};
        cooldowns.compute(rule.getId(), (id, last) -> {
            if (last == null || Duration.between(last, now).getSeconds() >= cooldownSeconds) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }

    private void markRejected(Long executionId) {
        try {
            ruleExecutionMapper.markFailed(executionId, 1, "执行队列已满", LocalDateTime.now());
        } catch (Exception e) {
            log.warn("规则执行记录置失败异常: executionId={}", executionId, e);
        }
    }

    private List<RuleDefinition> loadRules(Long userId) {
        long ttlSeconds = properties.getCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return ruleDefinitionMapper.selectEnabledByUser(userId);
        }
        long now = System.currentTimeMillis();
        CachedRules cached = cache.get(userId);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.rules();
        }
        List<RuleDefinition> rules = ruleDefinitionMapper.selectEnabledByUser(userId);
        cache.put(userId, new CachedRules(List.copyOf(rules), now + ttlSeconds * 1000L));
        return rules;
    }

    private Device loadDevice(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || device.getOwnerId() == null) {
            return null;
        }
        return device;
    }

    private static boolean inScope(RuleDefinition rule, Long deviceId) {
        return rule.getDeviceId() == null || rule.getDeviceId().equals(deviceId);
    }

    private static boolean identifierEquals(String ruleIdentifier, String sampleIdentifier) {
        return ruleIdentifier != null && ruleIdentifier.equals(sampleIdentifier);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** 用户规则缓存条目：规则列表 + 过期时刻（毫秒）。 */
    private record CachedRules(List<RuleDefinition> rules, long expiresAtMillis) {
    }
}