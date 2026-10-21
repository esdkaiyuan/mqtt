package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.service.RuleEvaluationService.EventSample;
import com.mqtt.cloud.service.RuleEvaluationService.PropertySample;
import com.mqtt.cloud.service.RuleMultiSpec.Condition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则**多条件**求值（T-33 / G-6-7）。
 * <p>
 * 与单条件路径的关系是**并存而非替换**：单条件规则（{@code conditions_json} 为空）仍走
 * {@code RuleEvaluationServiceImpl} 里原有的「逐样本匹配」代码，一行未改；本类只服务于多条件规则。
 * 这样存量规则的触发次数、触发值语义全部原样保留。
 * <p>
 * 求值范围是**一次上报批**而非单个样本：多条件的 identifier 天然分散在不同样本里
 * （「温度 &gt; 30 且 湿度 &lt; 60」需要设备同时报两个属性），因此先按 identifier 归组再逐条件求值。
 * <p>
 * 三条语义决策：
 * <ul>
 *   <li><b>同一条件多次上报取「任一满足」</b>：设备一批里重复上报同一属性，只要有一次越限就应触发，
 *       取交集会让偶发尖峰漏报。</li>
 *   <li><b>{@code AND} 下缺值 = 不满足</b>：本批没带的属性就是没上报，不能因为「没数据」就判定为命中。</li>
 *   <li><b>{@code OR} 下缺值不贡献命中</b>，但若所有条件都缺值则整体不命中 —— 否则一条永远不会被
 *       满足的规则会被另一个条件的缺值误触发。</li>
 * </ul>
 * 纯函数、无状态，可被 {@code RuleTestService} 的试运行干跑直接复用，保证「试运行所见即线上所判」。
 */
public final class RuleMultiMatcher {

    private RuleMultiMatcher() {
    }

    /** 多条件求值结论。 */
    public record Result(boolean hit, String triggerIdentifier, String triggerValue) {
        static Result miss() {
            return new Result(false, null, null);
        }
    }

    /**
     * 属性多条件求值。
     *
     * @param logic     {@code AND} / {@code OR}
     * @param conditions 条件列表，非空
     * @param samples   本批属性样本（可为空）
     * @return 命中结论；命中时带触发标识符与触发值（取首个满足条件的值）
     */
    public static Result matchProperties(String logic, List<Condition> conditions,
                                         List<PropertySample> samples) {
        if (conditions == null || conditions.isEmpty()) {
            return Result.miss();
        }
        boolean andMode = !RuleConstants.LOGIC_OR.equals(logic);
        Map<String, List<String>> byIdentifier = groupProperties(samples);
        boolean anySatisfied = false;
        Result firstHit = null;
        for (Condition condition : conditions) {
            String identifier = condition.identifier();
            if (identifier == null) {
                // 条件本身不完整：AND 下直接判不满足，OR 下跳过（服务层校验已挡在入库前，这里是兜底）
                if (andMode) {
                    return Result.miss();
                }
                continue;
            }
            List<String> values = byIdentifier.get(identifier);
            if (values == null || values.isEmpty()) {
                // AND 下缺值必须**终止**求值而不是「跳过」：AND 要求全部条件都成立，
                // 若只 continue，后面某个条件满足时 anySatisfied 变 true，整条规则会被误判命中。
                if (andMode) {
                    return Result.miss();
                }
                // OR 下缺值不贡献命中，继续看下一个条件
                continue;
            }
            Boolean satisfied = anySatisfies(values, condition.operator(), condition.thresholdValue());
            if (!Boolean.TRUE.equals(satisfied)) {
                if (andMode) {
                    return Result.miss();
                }
                continue;
            }
            anySatisfied = true;
            if (firstHit == null) {
                firstHit = new Result(true, identifier, values.get(0));
            }
            if (!andMode) {
                // OR：任一条件满足即可短路
                return firstHit;
            }
        }
        // AND 走完全部条件且至少一个真正满足才算命中（全部条件缺值时 anySatisfied=false）
        return andMode && anySatisfied ? firstHit : Result.miss();
    }

    /**
     * 事件多条件求值。
     * <p>
     * 事件条件没有「比较」，只有标识符 + 可选事件类型，因此求值退化为「是否存在满足该条件的样本」。
     *
     * @param logic      {@code AND} / {@code OR}
     * @param conditions 条件列表，非空
     * @param samples    本批事件样本（可为空）
     */
    public static Result matchEvents(String logic, List<Condition> conditions,
                                     List<EventSample> samples) {
        if (conditions == null || conditions.isEmpty()) {
            return Result.miss();
        }
        boolean andMode = !RuleConstants.LOGIC_OR.equals(logic);
        boolean anySatisfied = false;
        Result firstHit = null;
        for (Condition condition : conditions) {
            String identifier = condition.identifier();
            if (identifier == null) {
                if (andMode) {
                    return Result.miss();
                }
                continue;
            }
            EventSample matched = findSample(samples, identifier, condition.eventType());
            if (matched == null) {
                // 同属性路径：AND 下缺样本即不满足，必须终止求值
                if (andMode) {
                    return Result.miss();
                }
                continue;
            }
            anySatisfied = true;
            if (firstHit == null) {
                firstHit = new Result(true, identifier, matched.outputData());
            }
            if (!andMode) {
                return firstHit;
            }
        }
        return andMode && anySatisfied ? firstHit : Result.miss();
    }

    /** 按 identifier 归组，保留上报顺序（触发值取首个满足条件的原始值）。 */
    private static Map<String, List<String>> groupProperties(List<PropertySample> samples) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        if (samples == null) {
            return grouped;
        }
        for (PropertySample sample : samples) {
            if (sample == null || sample.identifier() == null) {
                continue;
            }
            grouped.computeIfAbsent(sample.identifier(), key -> new java.util.ArrayList<>())
                    .add(sample.valueText());
        }
        return grouped;
    }

    /** 同一标识符的多个上报值中，任一满足即认为该条件满足。 */
    private static Boolean anySatisfies(List<String> values, String operator, String threshold) {
        for (String value : values) {
            if (Boolean.TRUE.equals(RuleConditionMatcher.matchesProperty(value, operator, threshold))) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /** 找出第一条标识符与事件类型均满足的样本。 */
    private static EventSample findSample(List<EventSample> samples, String identifier, String eventType) {
        if (samples == null) {
            return null;
        }
        for (EventSample sample : samples) {
            if (sample != null
                    && RuleConditionMatcher.matchesEvent(identifier, eventType,
                    sample.identifier(), sample.eventType())) {
                return sample;
            }
        }
        return null;
    }
}