package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.entity.AlertRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警阈值内存窗口聚合（T-52）。
 * <p>
 * 按 {@code (ruleId, deviceId)} 维护滚动窗口；样本使用上报时间，{@code anchor} 取已见最大时间，
 * 避免乱序样本把窗口反向拉长。窗口只保存在当前副本：设备上行由共享订阅路由到单副本，
 * 正常链路可用；重启 / 重平衡会丢失窗口，后续如需跨副本再替换为 Redis / 流处理实现。
 * <p>
 * 非数值样本直接忽略；窗口达到 {@code minSamples} 后才返回聚合值，样本不足时调用方不得触发 / 恢复。
 */
@Slf4j
@Component
public class AlertWindowAggregator {

    static final int MAX_SAMPLES = 10_000;
    static final int MAX_KEYS = 10_000;

    private final ConcurrentHashMap<WindowKey, WindowState> windows = new ConcurrentHashMap<>();

    /**
     * 把样本计入窗口并返回聚合值；窗口未启用、样本非数值或不足最少样本时返回空。
     */
    public Optional<Aggregate> accept(AlertRule rule, Long deviceId,
                                      LocalDateTime reportedAt, String valueText) {
        if (rule == null || rule.getId() == null || deviceId == null || reportedAt == null) {
            return Optional.empty();
        }
        int windowSeconds = rule.getWindowSeconds() == null ? 0 : rule.getWindowSeconds();
        if (windowSeconds <= 0) {
            return Optional.empty();
        }
        BigDecimal value;
        try {
            value = new BigDecimal(valueText == null ? "" : valueText.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
        int minSamples = rule.getMinSamples() == null ? 1 : rule.getMinSamples();
        String aggregateType = normalize(rule.getAggregateType());

        WindowKey key = new WindowKey(rule.getId(), deviceId);
        if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
            // 保护性上限：规则 / 设备规模异常时宁可重建窗口，也不让进程无界增长
            windows.clear();
            log.warn("告警窗口数量超过 {}，已清空重建", MAX_KEYS);
        }
        WindowState state = windows.computeIfAbsent(key, ignored -> new WindowState());
        return state.add(value, reportedAt, windowSeconds, aggregateType, minSamples);
    }

    /** 规则更新 / 删除后清理窗口，避免旧窗口继续参与新配置评估。 */
    public void clearRule(Long ruleId) {
        if (ruleId == null) {
            return;
        }
        windows.keySet().removeIf(key -> ruleId.equals(key.ruleId()));
    }

    int activeWindowCount() {
        return windows.size();
    }

    private static String normalize(String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return AlertConstants.AGGREGATE_AVG;
        }
        String normalized = aggregateType.trim().toUpperCase(Locale.ROOT);
        return AlertConstants.AGGREGATES.contains(normalized) ? normalized : AlertConstants.AGGREGATE_AVG;
    }

    private record WindowKey(Long ruleId, Long deviceId) {
    }

    private record Sample(LocalDateTime reportedAt, BigDecimal value) {
    }

    /** 聚合结果：valueText 用于比较与告警落库，sampleCount 用于排障 / 标题。 */
    public record Aggregate(String valueText, int sampleCount) {
    }

    private static final class WindowState {
        private final ArrayDeque<Sample> samples = new ArrayDeque<>();
        private LocalDateTime anchor;

        private synchronized Optional<Aggregate> add(BigDecimal value, LocalDateTime reportedAt,
                                                     int windowSeconds, String aggregateType,
                                                     int minSamples) {
            if (anchor == null || reportedAt.isAfter(anchor)) {
                anchor = reportedAt;
            }
            samples.addLast(new Sample(reportedAt, value));
            LocalDateTime start = anchor.minusSeconds(windowSeconds);
            // 上报可能乱序，不能只看队首；统一移除窗口外的所有样本
            samples.removeIf(sample -> sample.reportedAt().isBefore(start));
            while (samples.size() > MAX_SAMPLES) {
                samples.removeFirst();
            }
            if (samples.size() < Math.max(1, minSamples)) {
                return Optional.empty();
            }
            return Optional.of(new Aggregate(aggregate(aggregateType), samples.size()));
        }

        private String aggregate(String type) {
            if (AlertConstants.AGGREGATE_COUNT.equals(type)) {
                return String.valueOf(samples.size());
            }
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal min = null;
            BigDecimal max = null;
            for (Sample sample : samples) {
                sum = sum.add(sample.value());
                min = min == null || sample.value().compareTo(min) < 0 ? sample.value() : min;
                max = max == null || sample.value().compareTo(max) > 0 ? sample.value() : max;
            }
            BigDecimal result = switch (type) {
                case AlertConstants.AGGREGATE_MIN -> min;
                case AlertConstants.AGGREGATE_MAX -> max;
                case AlertConstants.AGGREGATE_SUM -> sum;
                default -> sum.divide(BigDecimal.valueOf(samples.size()), 12, RoundingMode.HALF_UP);
            };
            return plain(result == null ? BigDecimal.ZERO : result);
        }

        private String plain(BigDecimal value) {
            BigDecimal normalized = value.stripTrailingZeros();
            if (normalized.scale() < 0) {
                normalized = normalized.setScale(0, RoundingMode.UNNECESSARY);
            }
            return normalized.toPlainString();
        }
    }
}
