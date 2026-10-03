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
import com.mqtt.cloud.service.RuleEvaluationService.EventSample;
import com.mqtt.cloud.service.RuleEvaluationService.PropertySample;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 消息规则评估单测（T-19 实施计划 P7）。
 * <p>
 * 覆盖缓存加载与失效、作用域 / 来源 / 标识符 / 事件类型过滤、条件判定（数值与文本、不可解析不命中）、
 * 冷却窗口（窗口内一次 / 关闭冷却重复触发）、总开关关闭，以及旁路异常不冒泡、落库失败不投递。
 */
class RuleEvaluationServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long DEVICE_ID = 100L;
    private static final Long OTHER_DEVICE_ID = 200L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long RULE_ID = 5L;
    private static final Long EXECUTION_ID = 900L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 10, 0, 0);

    private RuleDefinitionMapper ruleDefinitionMapper;
    private DeviceMapper deviceMapper;
    private RuleExecutionMapper ruleExecutionMapper;
    private RuleActionExecutor ruleActionExecutor;
    private RuleProperties properties;
    private ThreadPoolTaskExecutor ruleExecutor;
    private RuleEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        ruleDefinitionMapper = mock(RuleDefinitionMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        ruleExecutionMapper = mock(RuleExecutionMapper.class);
        ruleActionExecutor = mock(RuleActionExecutor.class);
        properties = new RuleProperties();
        // 默认关闭缓存，使各用例的规则桩可独立生效；缓存行为由专门用例覆盖
        properties.setCacheTtlSeconds(0);
        ruleExecutor = mock(ThreadPoolTaskExecutor.class);
        service = new RuleEvaluationServiceImpl(ruleDefinitionMapper, deviceMapper, ruleExecutionMapper,
                ruleActionExecutor, properties, ruleExecutor, new SimpleMeterRegistry());
        // 让投递到线程池的任务在当前线程同步执行，便于断言动作执行入口
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(ruleExecutor).execute(any(Runnable.class));
    }

    // ---------- 属性规则 ----------

    @Test
    void onProperties_should_trigger_matched_rule() {
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        ArgumentCaptor<RuleExecution> captor = ArgumentCaptor.forClass(RuleExecution.class);
        verify(ruleExecutionMapper).insert(captor.capture());
        RuleExecution saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(RuleConstants.STATUS_PENDING);
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(saved.getNextAttemptAt()).isNull();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getDeviceKey()).isEqualTo("dev-1");
        assertThat(saved.getTriggerValue()).isEqualTo("41");
        verify(ruleActionExecutor).execute(EXECUTION_ID);
    }

    @Test
    void onProperties_should_skip_when_disabled() {
        properties.setEnabled(false);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verifyNoInteractions(deviceMapper, ruleDefinitionMapper, ruleExecutionMapper, ruleActionExecutor);
    }

    @Test
    void onProperties_should_skip_unknown_device() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(null);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verifyNoInteractions(ruleDefinitionMapper, ruleExecutionMapper, ruleActionExecutor);
    }

    @Test
    void onProperties_should_skip_rule_out_of_scope() {
        RuleDefinition scoped = propertyRule();
        scoped.setDeviceId(OTHER_DEVICE_ID);
        stubDeviceAndRules(scoped);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(ruleExecutionMapper, never()).insert(any(RuleExecution.class));
        verify(ruleActionExecutor, never()).execute(any());
    }

    @Test
    void onProperties_should_skip_when_condition_not_matched() {
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "30", NOW)));

        verify(ruleExecutionMapper, never()).insert(any(RuleExecution.class));
    }

    @Test
    void onProperties_should_skip_unparseable_value() {
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "hot", NOW)));

        verify(ruleExecutionMapper, never()).insert(any(RuleExecution.class));
    }

    @Test
    void onProperties_should_ignore_event_source_rule() {
        stubDeviceAndRules(eventRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(ruleExecutionMapper, never()).insert(any(RuleExecution.class));
    }

    @Test
    void onProperties_should_ignore_other_identifier() {
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("humidity", "99", NOW)));

        verify(ruleExecutionMapper, never()).insert(any(RuleExecution.class));
    }

    // ---------- 事件规则 ----------

    @Test
    void onEvents_should_match_identifier_and_event_type() {
        stubDeviceAndRules(eventRule());

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "alert", "{\"level\":3}", NOW)));

        ArgumentCaptor<RuleExecution> captor = ArgumentCaptor.forClass(RuleExecution.class);
        verify(ruleExecutionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo(RuleConstants.SOURCE_EVENT);
        assertThat(captor.getValue().getIdentifier()).isEqualTo("fall");
        verify(ruleActionExecutor).execute(EXECUTION_ID);
    }

    @Test
    void onEvents_should_skip_when_event_type_mismatch() {
        stubDeviceAndRules(eventRule());

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "info", "{}", NOW)));

        verify(ruleExecutionMapper, never()).insert(any(RuleExecution.class));
    }

    @Test
    void onEvents_should_match_any_type_when_rule_type_absent() {
        RuleDefinition rule = eventRule();
        rule.setEventType(null);
        stubDeviceAndRules(rule);

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "info", "{}", NOW)));

        verify(ruleExecutionMapper).insert(any(RuleExecution.class));
    }

    // ---------- 冷却 ----------

    @Test
    void cooldown_should_suppress_repeat_within_window() {
        RuleDefinition rule = propertyRule();
        rule.setCooldownSeconds(60);
        stubDeviceAndRules(rule);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "42", NOW)));

        verify(ruleExecutionMapper, times(1)).insert(any(RuleExecution.class));
    }

    @Test
    void cooldown_zero_should_allow_repeat() {
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "42", NOW)));

        verify(ruleExecutionMapper, times(2)).insert(any(RuleExecution.class));
    }

    // ---------- 缓存 ----------

    @Test
    void cache_should_reuse_rules_within_ttl() {
        properties.setCacheTtlSeconds(60);
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(ruleDefinitionMapper, times(1)).selectEnabledByUser(USER_ID);
    }

    @Test
    void evictCache_should_force_reload() {
        properties.setCacheTtlSeconds(60);
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.evictCache(USER_ID);
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(ruleDefinitionMapper, times(2)).selectEnabledByUser(USER_ID);
    }

    @Test
    void ttl_zero_should_disable_cache() {
        properties.setCacheTtlSeconds(0);
        stubDeviceAndRules(propertyRule());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(ruleDefinitionMapper, times(2)).selectEnabledByUser(USER_ID);
    }

    // ---------- 隔离 ----------

    @Test
    void should_not_propagate_when_rule_load_fails() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());
        when(ruleDefinitionMapper.selectEnabledByUser(USER_ID)).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "41", NOW)))).doesNotThrowAnyException();
    }

    @Test
    void should_not_dispatch_when_execution_insert_fails() {
        stubDeviceAndRules(propertyRule());
        when(ruleExecutionMapper.insert(any(RuleExecution.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "41", NOW)))).doesNotThrowAnyException();
        verify(ruleExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void should_mark_failed_when_executor_rejects() {
        stubDeviceAndRules(propertyRule());
        doThrow(new RejectedExecutionException("full")).when(ruleExecutor).execute(any(Runnable.class));

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(ruleExecutionMapper).markFailed(eq(EXECUTION_ID), eq(1), anyString(), any());
        verify(ruleActionExecutor, never()).execute(any());
    }

    // ---------- 辅助 ----------

    private void stubDeviceAndRules(RuleDefinition... rules) {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());
        when(ruleDefinitionMapper.selectEnabledByUser(USER_ID)).thenReturn(List.of(rules));
        doAnswer(invocation -> {
            ((RuleExecution) invocation.getArgument(0)).setId(EXECUTION_ID);
            return 1;
        }).when(ruleExecutionMapper).insert(any(RuleExecution.class));
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceKey("dev-1");
        device.setDeviceName("设备1");
        device.setOwnerId(USER_ID);
        return device;
    }

    private RuleDefinition propertyRule() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setName("高温联动");
        rule.setSourceType(RuleConstants.SOURCE_PROPERTY);
        rule.setIdentifier("temperature");
        rule.setOperator(RuleConstants.OPERATOR_GT);
        rule.setThresholdValue("40");
        rule.setActionType(RuleConstants.ACTION_UPDATE_PROPERTY);
        rule.setCooldownSeconds(0);
        rule.setEnabled(1);
        return rule;
    }

    private RuleDefinition eventRule() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setName("跌倒事件联动");
        rule.setSourceType(RuleConstants.SOURCE_EVENT);
        rule.setIdentifier("fall");
        rule.setEventType("alert");
        rule.setActionType(RuleConstants.ACTION_UPDATE_PROPERTY);
        rule.setCooldownSeconds(0);
        rule.setEnabled(1);
        return rule;
    }
}