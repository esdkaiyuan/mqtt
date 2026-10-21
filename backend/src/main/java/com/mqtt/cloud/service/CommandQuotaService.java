package com.mqtt.cloud.service;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.CommandProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 命令调用配额（T-34 / G-4-1）。
 * <p>
 * 按**操作者维度**的固定窗口计数，口径对齐 {@code ApiKeyRateLimitFilter}（Redis 键计数 + 窗口过期），
 * 但把两个维度的差异摆明：
 * <ul>
 *   <li><b>为什么按 operatorId 而不是 API Key</b>：API Key 侧已有 {@code app.rate-limit} 覆盖；
 *       缺的是**控制台用户**维度的约束。控制台是人在操作，配额必须落到人。</li>
 *   <li><b>为什么覆盖全部来源</b>：内部触发（规则 / 场景 / OTA）的 {@code operatorId} 是「谁配置的这条自动化」，
 *       把它作为配额维度可防止规则风暴把 Broker 与设备打爆 —— 这恰恰是命令配额最有价值的场景。
 *       权限判定（{@link CommandGuardService}）则相反，只管外部来源。</li>
 * </ul>
 * 两者刻意用不同维度与不同范围：**权限管「谁能发起」，配额管「发起多频繁」**，不该互相牵制。
 * <p>
 * <b>默认关闭</b>：{@code enabled=false} 时本类不做任何 Redis 读写，零开销；
 * 未显式配置就强制限流会把正常下发打成 429。
 * <p>
 * <b>同步调用更贵</b>：{@code callType=sync} 会阻塞等待设备回执，实际阈值取 {@code 阈值 × sync-multiplier}。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandQuotaService {

    private final CommandProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 消耗一次命令调用配额；超出时抛 {@code 4291}。
     * <p>
     * {@code operatorId} 为 {@code null}（无登录上下文的系统调用）时**直接放行** ——
     * 用一个共享的匿名桶去限制系统内部调用，等于把配额变成随机抽签。
     *
     * @param operatorId 操作者；为空表示不计量
     * @param callType   调用类型（{@code sync} 时收紧阈值）
     */
    public void consume(Long operatorId, String callType) {
        CommandProperties.Quota quota = properties.getQuota();
        if (quota == null || !quota.isEnabled() || operatorId == null) {
            return;
        }
        int limit = resolveLimit(quota, callType);
        if (limit <= 0) {
            return;
        }
        String key = quota.getKeyPrefix() + operatorId;
        long current;
        try {
            current = incrementWithWindow(key, quota);
        } catch (Exception e) {
            // Redis 抖动不应该阻断业务下发：按 failOpen 决定降级方向并留下 WARN
            if (quota.isFailOpen()) {
                log.warn("命令配额计数失败，按 failOpen 放行: operatorId={}", operatorId, e);
                return;
            }
            throw new BusinessException(ResultCode.COMMAND_QUOTA_EXCEEDED, "命令调用配额检查失败，请稍后重试");
        }
        if (current > limit) {
            log.warn("命令调用超配额: operatorId={}, current={}, limit={}", operatorId, current, limit);
            throw new BusinessException(ResultCode.COMMAND_QUOTA_EXCEEDED,
                    "命令调用超出配额（每 " + quota.getWindowSeconds() + " 秒至多 " + limit + " 次）");
        }
    }

    /** 同步调用的实际阈值：基准阈值 × 倍数；倍数未配置或非正时按 1 处理。 */
    private int resolveLimit(CommandProperties.Quota quota, String callType) {
        int base = quota.getMaxPerWindow();
        if (!DeviceCommandService.CALL_TYPE_SYNC.equalsIgnoreCase(callType)) {
            return base;
        }
        int multiplier = quota.getSyncMultiplier() <= 0 ? 1 : quota.getSyncMultiplier();
        long limit = (long) base * multiplier;
        return limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit;
    }

    /**
     * 自增并在首次写入时设置窗口过期。
     * <p>
     * 只在第一次（返回值为 1）设 TTL：每次 INCR 都重设过期会把窗口无限续期，
     * 退化成「从第一次调用起永不释放」的滑动窗口 —— 那不是配额，是累计上限。
     */
    private long incrementWithWindow(String key, CommandProperties.Quota quota) {
        Long value = redisTemplate.opsForValue().increment(key);
        long current = value == null ? 1L : value;
        if (current == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(Math.max(1, quota.getWindowSeconds())));
        }
        return current;
    }

    /** 当前窗口剩余可用次数，仅供运维查询与测试；未启用或无数据时返回 {@code -1}。 */
    public long remaining(Long operatorId) {
        CommandProperties.Quota quota = properties.getQuota();
        if (quota == null || !quota.isEnabled() || operatorId == null) {
            return -1L;
        }
        try {
            String value = redisTemplate.opsForValue().get(quota.getKeyPrefix() + operatorId);
            if (value == null) {
                return quota.getMaxPerWindow();
            }
            long used = Long.parseLong(value);
            return Math.max(0L, quota.getMaxPerWindow() - used);
        } catch (Exception e) {
            log.warn("命令配额查询失败: operatorId={}", operatorId, e);
            return -1L;
        }
    }

    /** 当前窗口键的过期时刻，便于测试与运维排障；无数据时返回 {@code null}。 */
    public LocalDateTime windowExpiry(Long operatorId) {
        CommandProperties.Quota quota = properties.getQuota();
        if (quota == null || !quota.isEnabled() || operatorId == null) {
            return null;
        }
        try {
            Long expire = redisTemplate.getExpire(quota.getKeyPrefix() + operatorId);
            if (expire == null || expire < 0) {
                return null;
            }
            return LocalDateTime.now().plusSeconds(expire);
        } catch (Exception e) {
            log.warn("命令配额窗口查询失败: operatorId={}", operatorId, e);
            return null;
        }
    }

    /** 供测试与运维按需清理；生产不应随意调用。 */
    public void reset(Long operatorId) {
        if (operatorId == null) {
            return;
        }
        try {
            redisTemplate.delete(properties.getQuota().getKeyPrefix() + operatorId);
        } catch (Exception e) {
            log.warn("命令配额重置失败: operatorId={}", operatorId, e);
        }
    }
}