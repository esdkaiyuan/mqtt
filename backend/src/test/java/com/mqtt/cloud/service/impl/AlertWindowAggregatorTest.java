package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.entity.AlertRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警窗口聚合单测（T-52）。
 * <p>
 * 重点守住「窗口边界、乱序上报、最少样本」三个容易静默算错的点。
 */
class AlertWindowAggregatorTest {

    private static final Long RULE_ID = 10L;
    private static final Long DEVICE_ID = 100L;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 22, 12, 0, 0);

    private final AlertWindowAggregator aggregator = new AlertWindowAggregator();

    @Test
    void avg_should_average_samples_in_window() {
        AlertRule rule = rule(300, AlertConstants.AGGREGATE_AVG, 1);

        aggregator.accept(rule, DEVICE_ID, BASE, "10");
        aggregator.accept(rule, DEVICE_ID, BASE.plusSeconds(10), "20");
        Optional<AlertWindowAggregator.Aggregate> result =
                aggregator.accept(rule, DEVICE_ID, BASE.plusSeconds(20), "30");

        assertThat(result).isPresent();
        assertThat(result.get().valueText()).isEqualTo("20");
        assertThat(result.get().sampleCount()).isEqualTo(3);
    }

    @Test
    void min_max_sum_count_should_use_big_decimal() {
        AlertRule min = rule(1L, 300, AlertConstants.AGGREGATE_MIN, 1);
        AlertRule max = rule(2L, 300, AlertConstants.AGGREGATE_MAX, 1);
        AlertRule sum = rule(3L, 300, AlertConstants.AGGREGATE_SUM, 1);
        AlertRule count = rule(4L, 300, AlertConstants.AGGREGATE_COUNT, 1);

        aggregator.accept(min, DEVICE_ID, BASE, "1.5");
        aggregator.accept(min, DEVICE_ID, BASE.plusSeconds(1), "-2.5");
        aggregator.accept(max, DEVICE_ID, BASE, "1.5");
        aggregator.accept(max, DEVICE_ID, BASE.plusSeconds(1), "-2.5");
        aggregator.accept(sum, DEVICE_ID, BASE, "1.5");
        aggregator.accept(sum, DEVICE_ID, BASE.plusSeconds(1), "2.5");
        aggregator.accept(count, DEVICE_ID, BASE, "not-a-number");
        aggregator.accept(count, DEVICE_ID, BASE.plusSeconds(1), "9");

        assertThat(aggregator.accept(min, DEVICE_ID, BASE.plusSeconds(2), "0").orElseThrow()
                .valueText()).isEqualTo("-2.5");
        assertThat(aggregator.accept(max, DEVICE_ID, BASE.plusSeconds(2), "0").orElseThrow()
                .valueText()).isEqualTo("1.5");
        assertThat(aggregator.accept(sum, DEVICE_ID, BASE.plusSeconds(2), "0").orElseThrow()
                .valueText()).isEqualTo("4");
        // COUNT 只统计数值样本：前面的非数值样本不入窗
        assertThat(aggregator.accept(count, DEVICE_ID, BASE.plusSeconds(2), "1").orElseThrow()
                .valueText()).isEqualTo("2");
    }

    @Test
    void samples_outside_window_should_be_evicted() {
        AlertRule rule = rule(10, AlertConstants.AGGREGATE_AVG, 1);

        aggregator.accept(rule, DEVICE_ID, BASE, "10");
        Optional<AlertWindowAggregator.Aggregate> result =
                aggregator.accept(rule, DEVICE_ID, BASE.plusSeconds(11), "20");

        assertThat(result).isPresent();
        assertThat(result.get().sampleCount()).isEqualTo(1);
        assertThat(result.get().valueText()).isEqualTo("20");
    }

    @Test
    void out_of_order_sample_should_not_extend_window() {
        AlertRule rule = rule(10, AlertConstants.AGGREGATE_AVG, 1);

        aggregator.accept(rule, DEVICE_ID, BASE.plusSeconds(10), "10");
        Optional<AlertWindowAggregator.Aggregate> result =
                aggregator.accept(rule, DEVICE_ID, BASE.minusSeconds(5), "999");

        assertThat(result).isPresent();
        assertThat(result.get().sampleCount()).isEqualTo(1);
        assertThat(result.get().valueText()).isEqualTo("10");
    }

    @Test
    void insufficient_samples_should_return_empty() {
        AlertRule rule = rule(300, AlertConstants.AGGREGATE_AVG, 3);

        assertThat(aggregator.accept(rule, DEVICE_ID, BASE, "10")).isEmpty();
        assertThat(aggregator.accept(rule, DEVICE_ID, BASE.plusSeconds(1), "20")).isEmpty();
        assertThat(aggregator.accept(rule, DEVICE_ID, BASE.plusSeconds(2), "30")).isPresent();
    }

    @Test
    void non_numeric_sample_should_be_ignored() {
        AlertRule rule = rule(300, AlertConstants.AGGREGATE_AVG, 1);

        assertThat(aggregator.accept(rule, DEVICE_ID, BASE, "hot")).isEmpty();
        assertThat(aggregator.accept(rule, DEVICE_ID, BASE.plusSeconds(1), "10").orElseThrow()
                .valueText()).isEqualTo("10");
    }

    @Test
    void clear_rule_should_drop_its_window() {
        AlertRule rule = rule(300, AlertConstants.AGGREGATE_AVG, 1);
        aggregator.accept(rule, DEVICE_ID, BASE, "10");
        assertThat(aggregator.activeWindowCount()).isEqualTo(1);

        aggregator.clearRule(RULE_ID);

        assertThat(aggregator.activeWindowCount()).isZero();
    }

    private AlertRule rule(int windowSeconds, String aggregateType, int minSamples) {
        return rule(RULE_ID, windowSeconds, aggregateType, minSamples);
    }

    private AlertRule rule(Long ruleId, int windowSeconds, String aggregateType, int minSamples) {
        AlertRule rule = new AlertRule();
        rule.setId(ruleId);
        rule.setWindowSeconds(windowSeconds);
        rule.setAggregateType(aggregateType);
        rule.setMinSamples(minSamples);
        return rule;
    }
}
