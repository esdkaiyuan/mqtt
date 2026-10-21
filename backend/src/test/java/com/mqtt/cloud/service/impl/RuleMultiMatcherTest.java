package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.service.RuleEvaluationService.EventSample;
import com.mqtt.cloud.service.RuleEvaluationService.PropertySample;
import com.mqtt.cloud.service.RuleMultiSpec;
import com.mqtt.cloud.service.RuleMultiSpec.Action;
import com.mqtt.cloud.service.RuleMultiSpec.Condition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多条件求值与多动作解析的纯函数单测（T-33 / G-6-7）。
 * <p>
 * 与 {@code RuleEvaluationServiceImplTest} 分开：后者守「存量单条件路径行为不变」，
 * 本类守「新增的多条件语义」。两类失败指向不同方向。
 */
class RuleMultiMatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------- 组合逻辑 ----------------

    @Test
    void and_requires_all_conditions() {
        Condition hot = new Condition("temperature", RuleConstants.OPERATOR_GT, "30", null);
        Condition humid = new Condition("humidity", RuleConstants.OPERATOR_LT, "60", null);

        RuleMultiMatcher.Result both = RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND,
                List.of(hot, humid), samples("temperature", "35", "humidity", "50"));
        assertThat(both.hit()).isTrue();
        assertThat(both.triggerIdentifier()).isEqualTo("temperature");

        RuleMultiMatcher.Result one = RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND,
                List.of(hot, humid), samples("temperature", "35", "humidity", "80"));
        assertThat(one.hit()).as("湿度不满足 → AND 不命中").isFalse();
    }

    @Test
    void or_requires_any_condition() {
        Condition hot = new Condition("temperature", RuleConstants.OPERATOR_GT, "30", null);
        Condition humid = new Condition("humidity", RuleConstants.OPERATOR_LT, "60", null);

        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_OR, List.of(hot, humid),
                samples("temperature", "35", "humidity", "80")).hit()).isTrue();
        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_OR, List.of(hot, humid),
                samples("temperature", "20", "humidity", "50")).hit()).isTrue();
        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_OR, List.of(hot, humid),
                samples("temperature", "20", "humidity", "80")).hit()).isFalse();
    }

    @Test
    void missing_value_is_not_a_hit_under_and() {
        Condition hot = new Condition("temperature", RuleConstants.OPERATOR_GT, "30", null);
        Condition humid = new Condition("humidity", RuleConstants.OPERATOR_LT, "60", null);

        // 设备只报了温度：湿度缺值不能被当成「满足」，否则数据缺失会触发规则
        RuleMultiMatcher.Result result = RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND,
                List.of(hot, humid), samples("temperature", "35"));
        assertThat(result.hit()).isFalse();
    }

    @Test
    void all_conditions_missing_never_hits_even_under_or() {
        Condition hot = new Condition("temperature", RuleConstants.OPERATOR_GT, "30", null);
        Condition humid = new Condition("humidity", RuleConstants.OPERATOR_LT, "60", null);

        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_OR, List.of(hot, humid),
                samples("pressure", "101")).hit()).isFalse();
    }

    @Test
    void repeated_identifier_hits_if_any_value_satisfies() {
        Condition hot = new Condition("temperature", RuleConstants.OPERATOR_GT, "30", null);

        // 设备一批里重复上报温度：一次尖峰就应触发，取交集会漏报偶发尖峰
        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND, List.of(hot),
                samples("temperature", "20", "temperature", "35")).hit()).isTrue();
    }

    @Test
    void unparsable_value_does_not_hit() {
        Condition hot = new Condition("temperature", RuleConstants.OPERATOR_GT, "30", null);

        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND, List.of(hot),
                samples("temperature", "N/A")).hit()).isFalse();
    }

    @Test
    void empty_conditions_and_empty_samples_never_hit() {
        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND, List.of(), samples("t", "1")).hit())
                .isFalse();
        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND,
                List.of(new Condition("t", RuleConstants.OPERATOR_GT, "1", null)), List.of()).hit()).isFalse();
        assertThat(RuleMultiMatcher.matchProperties(RuleConstants.LOGIC_AND, null, samples("t", "1")).hit())
                .isFalse();
    }

    // ---------------- 事件多条件 ----------------

    @Test
    void event_conditions_respect_event_type_filter() {
        Condition alert = new Condition("overheat", null, null, "alert");
        Condition fault = new Condition("overheat", null, null, "fault");

        List<EventSample> alertSample = List.of(
                new EventSample("overheat", "alert", "{\"t\":95}", LocalDateTime.now()));

        assertThat(RuleMultiMatcher.matchEvents(RuleConstants.LOGIC_AND, List.of(alert), alertSample).hit())
                .isTrue();
        assertThat(RuleMultiMatcher.matchEvents(RuleConstants.LOGIC_AND, List.of(fault), alertSample).hit())
                .as("事件类型不匹配不应命中").isFalse();
    }

    @Test
    void event_and_logic_requires_every_sample() {
        Condition a = new Condition("overheat", null, null, null);
        Condition b = new EventConditionFactory().build("smoke", null);

        List<EventSample> onlyOverheat = List.of(
                new EventSample("overheat", "alert", "{}", LocalDateTime.now()));

        assertThat(RuleMultiMatcher.matchEvents(RuleConstants.LOGIC_AND, List.of(a, b), onlyOverheat).hit())
                .isFalse();
        assertThat(RuleMultiMatcher.matchEvents(RuleConstants.LOGIC_OR, List.of(a, b), onlyOverheat).hit())
                .isTrue();
    }

    /** 测试辅助：等价于 {@code new Condition(id, null, null, null)}，避免重复书写。 */
    private static final class EventConditionFactory {
        Condition build(String identifier, String eventType) {
            return new Condition(identifier, null, null, eventType);
        }
    }

    // ---------------- 解析与回落 ----------------

    @Test
    void conditions_fall_back_to_single_columns_when_json_absent() {
        RuleMultiSpecHolder holder = new RuleMultiSpecHolder();
        holder.rule.setIdentifier("temperature");
        holder.rule.setOperator(RuleConstants.OPERATOR_GT);
        holder.rule.setThresholdValue("30");

        List<Condition> conditions = RuleMultiSpec.conditionsOf(holder.rule, objectMapper);

        assertThat(conditions).containsExactly(
                new Condition("temperature", RuleConstants.OPERATOR_GT, "30", null));
        assertThat(RuleMultiSpec.hasMultipleConditions(holder.rule, objectMapper)).isFalse();
    }

    @Test
    void actions_fall_back_to_single_columns_when_json_absent() {
        RuleMultiSpecHolder holder = new RuleMultiSpecHolder();
        holder.rule.setActionType(RuleConstants.ACTION_UPDATE_PROPERTY);
        holder.rule.setActionConfig("{\"identifier\":\"mode\",\"value\":\"auto\"}");

        List<Action> actions = RuleMultiSpec.actionsOf(holder.rule, objectMapper);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).actionType()).isEqualTo(RuleConstants.ACTION_UPDATE_PROPERTY);
        assertThat(RuleMultiSpec.hasMultipleActions(holder.rule, objectMapper)).isFalse();
    }

    @Test
    void multi_condition_json_is_parsed() {
        RuleMultiSpecHolder holder = new RuleMultiSpecHolder();
        holder.rule.setConditionsJson(
                "[{\"identifier\":\"temperature\",\"operator\":\"GT\",\"thresholdValue\":\"30\"},"
                        + "{\"identifier\":\"humidity\",\"operator\":\"LT\",\"thresholdValue\":\"60\"}]");

        List<Condition> conditions = RuleMultiSpec.conditionsOf(holder.rule, objectMapper);

        assertThat(RuleMultiSpec.hasMultipleConditions(holder.rule, objectMapper)).isTrue();
        assertThat(conditions).hasSize(2);
        assertThat(conditions.get(0).identifier()).isEqualTo("temperature");
        assertThat(conditions.get(1).operator()).isEqualTo(RuleConstants.OPERATOR_LT);
    }

    @Test
    void malformed_json_falls_back_instead_of_throwing() {
        RuleMultiSpecHolder holder = new RuleMultiSpecHolder();
        holder.rule.setIdentifier("temperature");
        holder.rule.setOperator(RuleConstants.OPERATOR_GT);
        holder.rule.setThresholdValue("30");
        holder.rule.setConditionsJson("{不是数组");

        // 一条规则的配置问题不应让同一用户所有规则停摆
        assertThat(RuleMultiSpec.hasMultipleConditions(holder.rule, objectMapper)).isFalse();
        assertThat(RuleMultiSpec.conditionsOf(holder.rule, objectMapper)).hasSize(1);
    }

    @Test
    void element_without_identifier_falls_back_whole() {
        RuleMultiSpecHolder holder = new RuleMultiSpecHolder();
        holder.rule.setIdentifier("temperature");
        holder.rule.setOperator(RuleConstants.OPERATOR_GT);
        holder.rule.setThresholdValue("30");
        holder.rule.setConditionsJson("[{\"operator\":\"GT\",\"thresholdValue\":\"30\"}]");

        assertThat(RuleMultiSpec.conditionsOf(holder.rule, objectMapper)).hasSize(1);
    }

    @Test
    void logic_defaults_to_and_when_illegal() {
        RuleMultiSpecHolder holder = new RuleMultiSpecHolder();
        assertThat(RuleMultiSpec.logicOf(holder.rule)).isEqualTo(RuleConstants.LOGIC_AND);

        holder.rule.setConditionLogic("XOR");
        assertThat(RuleMultiSpec.logicOf(holder.rule)).isEqualTo(RuleConstants.LOGIC_AND);

        holder.rule.setConditionLogic("or");
        assertThat(RuleMultiSpec.logicOf(holder.rule)).isEqualTo(RuleConstants.LOGIC_OR);
    }

    // ---------------- 辅助 ----------------

    private static List<PropertySample> samples(String... kv) {
        List<PropertySample> samples = new java.util.ArrayList<>(kv.length / 2);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            samples.add(new PropertySample(kv[i], kv[i + 1], now));
        }
        return samples;
    }

    /** 承载一个带默认值的规则实体，避免每个用例都手工 new。 */
    private static final class RuleMultiSpecHolder {
        private final com.mqtt.cloud.entity.RuleDefinition rule = new com.mqtt.cloud.entity.RuleDefinition();
    }
}