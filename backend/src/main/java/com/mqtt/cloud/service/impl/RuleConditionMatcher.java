package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.RuleConstants;

import java.math.BigDecimal;

/**
 * 规则条件判定（T-19 设计文档 §8.3）。
 * <p>
 * 判定口径与 T-17 告警**完全一致**：数值比较符（{@code GT/GTE/LT/LTE}）两侧按
 * {@code BigDecimal.compareTo}；{@code EQ}/{@code NE} 按 {@code trim()} 后文本相等；
 * 任一侧不可解析为数值 → 返回 {@code null}（不可判定，调用方视为不命中）。
 * <p>
 * 供 {@code RuleEvaluationServiceImpl}（上行匹配）与 {@code RuleServiceImpl}（试运行干跑）共用，
 * 避免同一份口径在两处各自实现而产生漂移。
 */
public final class RuleConditionMatcher {

    private RuleConditionMatcher() {
    }

    /**
     * 属性条件判定。
     *
     * @return {@code true} 命中 / {@code false} 未命中 / {@code null} 不可判定（值或阈值不可解析）
     */
    public static Boolean matchesProperty(String value, String operator, String threshold) {
        if (value == null || operator == null || threshold == null) {
            return null;
        }
        if (RuleConstants.NUMERIC_OPERATORS.contains(operator)) {
            BigDecimal left;
            BigDecimal right;
            try {
                left = new BigDecimal(value.trim());
                right = new BigDecimal(threshold.trim());
            } catch (NumberFormatException e) {
                return null;
            }
            int cmp = left.compareTo(right);
            switch (operator) {
                case RuleConstants.OPERATOR_GT:
                    return cmp > 0;
                case RuleConstants.OPERATOR_GTE:
                    return cmp >= 0;
                case RuleConstants.OPERATOR_LT:
                    return cmp < 0;
                case RuleConstants.OPERATOR_LTE:
                    return cmp <= 0;
                default:
                    return null;
            }
        }
        boolean equal = value.trim().equals(threshold.trim());
        if (RuleConstants.OPERATOR_EQ.equals(operator)) {
            return equal;
        }
        if (RuleConstants.OPERATOR_NE.equals(operator)) {
            return !equal;
        }
        return null;
    }

    /** 事件条件判定：标识符相等，且规则事件类型为空或不等于样本事件类型时视为匹配。 */
    public static boolean matchesEvent(String ruleIdentifier, String ruleEventType,
                                       String sampleIdentifier, String sampleEventType) {
        if (ruleIdentifier == null || !ruleIdentifier.equals(sampleIdentifier)) {
            return false;
        }
        return ruleEventType == null || ruleEventType.equals(sampleEventType);
    }
}