package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.RuleConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规则条件判定单测（T-19 实施计划 P7）。
 * <p>
 * 数值比较符按 {@code BigDecimal.compareTo}（含小数与负数）；{@code EQ}/{@code NE} 按 trim 后文本；
 * 任一侧不可解析为数值返回 {@code null}（不可判定），调用方视为不命中。
 */
class RuleConditionMatcherTest {

    @Test
    void numeric_operators_should_compare_by_bigdecimal() {
        assertThat(RuleConditionMatcher.matchesProperty("41", RuleConstants.OPERATOR_GT, "40")).isTrue();
        assertThat(RuleConditionMatcher.matchesProperty("40", RuleConstants.OPERATOR_GT, "40")).isFalse();
        assertThat(RuleConditionMatcher.matchesProperty("40", RuleConstants.OPERATOR_GTE, "40")).isTrue();
        assertThat(RuleConditionMatcher.matchesProperty("39", RuleConstants.OPERATOR_LT, "40")).isTrue();
        assertThat(RuleConditionMatcher.matchesProperty("40", RuleConstants.OPERATOR_LTE, "40")).isTrue();
        // EQ 不在数值比较符集合内，按 trim 后文本比较，故 "40.0" ≠ "40"（口径与 T-17 一致）
        assertThat(RuleConditionMatcher.matchesProperty("40.0", RuleConstants.OPERATOR_EQ, "40")).isFalse();
    }

    @Test
    void numeric_operators_should_tolerate_whitespace_and_signs() {
        assertThat(RuleConditionMatcher.matchesProperty(" 40.5 ", RuleConstants.OPERATOR_GT, "40")).isTrue();
        assertThat(RuleConditionMatcher.matchesProperty("-5", RuleConstants.OPERATOR_LT, "0")).isTrue();
    }

    @Test
    void numeric_operators_should_return_null_when_value_or_threshold_not_parseable() {
        assertThat(RuleConditionMatcher.matchesProperty("hot", RuleConstants.OPERATOR_GT, "40")).isNull();
        assertThat(RuleConditionMatcher.matchesProperty("41", RuleConstants.OPERATOR_GT, "warm")).isNull();
    }

    @Test
    void eq_ne_should_compare_trimmed_text() {
        assertThat(RuleConditionMatcher.matchesProperty("on", RuleConstants.OPERATOR_EQ, " on ")).isTrue();
        assertThat(RuleConditionMatcher.matchesProperty("on", RuleConstants.OPERATOR_NE, "off")).isTrue();
        assertThat(RuleConditionMatcher.matchesProperty("on", RuleConstants.OPERATOR_EQ, "off")).isFalse();
    }

    @Test
    void should_return_null_when_any_argument_missing() {
        assertThat(RuleConditionMatcher.matchesProperty(null, RuleConstants.OPERATOR_GT, "40")).isNull();
        assertThat(RuleConditionMatcher.matchesProperty("41", null, "40")).isNull();
        assertThat(RuleConditionMatcher.matchesProperty("41", RuleConstants.OPERATOR_GT, null)).isNull();
    }

    @Test
    void event_should_match_identifier_and_optional_event_type() {
        assertThat(RuleConditionMatcher.matchesEvent("overtemp", null, "overtemp", "fault")).isTrue();
        assertThat(RuleConditionMatcher.matchesEvent("overtemp", "fault", "overtemp", "fault")).isTrue();
        assertThat(RuleConditionMatcher.matchesEvent("overtemp", "info", "overtemp", "fault")).isFalse();
        assertThat(RuleConditionMatcher.matchesEvent("overtemp", null, "other", "fault")).isFalse();
        assertThat(RuleConditionMatcher.matchesEvent(null, null, "overtemp", "fault")).isFalse();
    }
}