package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.AlertRuleRequest;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRuleMapper;
import com.mqtt.cloud.service.DeviceScopeService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.scope.RuleScope;
import com.mqtt.cloud.util.ScopeJson;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警规则服务单测（T-17 实施计划 P7 / T-28 实施计划 P4）。
 * <p>
 * 覆盖三类来源的配置校验（非法统一 {@code 6208}）、作用域校验（非法 {@code 6268}）、
 * 设备归属校验（{@code 2003}）、详情归属（不存在 {@code 6207} / 越权 {@code 403}）与列表过滤。
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private AlertRuleMapper alertRuleMapper;

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceScopeService deviceScopeService;

    @Mock
    private AlertWindowAggregator windowAggregator;

    private AlertRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 需要实体的 TableInfo；纯单测无 MyBatis 上下文，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertRule.class);
        // AlertRuleServiceImpl 用显式构造器，Mockito 不再做字段注入，需手动装配依赖与 baseMapper
        service = new AlertRuleServiceImpl(deviceService, deviceScopeService,
                new ScopeJson(new ObjectMapper()), windowAggregator);
        ReflectionTestUtils.setField(service, "baseMapper", alertRuleMapper);
        // 作用域校验以纯函数桩替代，只保留「档位推断」语义，避免单测引入分组 / 标签服务
        lenient().when(deviceScopeService.validateAndNormalize(any(), any(), any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(1)));
    }

    /** 与 {@code DeviceScopeServiceImpl.inferType} 同口径：档位空则按 deviceId 推断。 */
    private static RuleScope normalize(RuleScope scope) {
        String type = scope.scopeType() == null
                ? (scope.deviceId() == null ? AlertConstants.SCOPE_ALL : AlertConstants.SCOPE_DEVICE)
                : scope.scopeType();
        return RuleScope.of(type, scope.deviceId(), scope.groupIds(), scope.tagIds());
    }

    @Test
    void create_should_reject_unknown_source_type() {
        AlertRuleRequest request = thresholdRequest();
        request.setSourceType("SOMETHING");

        assertCode(() -> service.create(USER_ID, request), ResultCode.ALERT_RULE_INVALID);
    }

    @Test
    void create_should_reject_threshold_without_identifier() {
        AlertRuleRequest request = thresholdRequest();
        request.setIdentifier(null);

        assertCode(() -> service.create(USER_ID, request), ResultCode.ALERT_RULE_INVALID);
    }

    @Test
    void create_should_reject_numeric_threshold_not_parseable() {
        AlertRuleRequest request = thresholdRequest();
        request.setThresholdValue("hot");

        assertCode(() -> service.create(USER_ID, request), ResultCode.ALERT_RULE_INVALID);
    }

    @Test
    void create_should_persist_window_threshold_config() {
        AlertRuleRequest request = thresholdRequest();
        request.setWindowSeconds(300);
        request.setAggregateType("avg");
        request.setMinSamples(3);

        service.create(USER_ID, request);

        ArgumentCaptor<AlertRule> captor = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getWindowSeconds()).isEqualTo(300);
        assertThat(captor.getValue().getAggregateType()).isEqualTo(AlertConstants.AGGREGATE_AVG);
        assertThat(captor.getValue().getMinSamples()).isEqualTo(3);
    }

    @Test
    void create_should_force_window_fields_off_for_event_rule() {
        AlertRuleRequest request = thresholdRequest();
        request.setSourceType(AlertConstants.SOURCE_EVENT);
        request.setWindowSeconds(300);
        request.setAggregateType(AlertConstants.AGGREGATE_MAX);
        request.setMinSamples(5);

        service.create(USER_ID, request);

        ArgumentCaptor<AlertRule> captor = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getWindowSeconds()).isZero();
        assertThat(captor.getValue().getAggregateType()).isNull();
        assertThat(captor.getValue().getMinSamples()).isEqualTo(1);
        assertThat(captor.getValue().getOperator()).isNull();
        assertThat(captor.getValue().getThresholdValue()).isNull();
    }

    @Test
    void create_should_reject_invalid_window_config() {
        AlertRuleRequest tooLong = thresholdRequest();
        tooLong.setWindowSeconds(86401);
        assertCode(() -> service.create(USER_ID, tooLong), ResultCode.ALERT_RULE_INVALID);

        AlertRuleRequest zeroSamples = thresholdRequest();
        zeroSamples.setWindowSeconds(300);
        zeroSamples.setMinSamples(0);
        assertCode(() -> service.create(USER_ID, zeroSamples), ResultCode.ALERT_RULE_INVALID);

        AlertRuleRequest badAggregate = thresholdRequest();
        badAggregate.setWindowSeconds(300);
        badAggregate.setAggregateType("MEDIAN");
        assertCode(() -> service.create(USER_ID, badAggregate), ResultCode.ALERT_RULE_INVALID);

        AlertRuleRequest nonNumeric = thresholdRequest();
        nonNumeric.setWindowSeconds(300);
        nonNumeric.setThresholdValue("hot");
        assertCode(() -> service.create(USER_ID, nonNumeric), ResultCode.ALERT_RULE_INVALID);
    }

    @Test
    void create_should_reject_suppress_window_out_of_range() {
        AlertRuleRequest request = thresholdRequest();
        request.setSuppressWindowSeconds(90000);

        assertCode(() -> service.create(USER_ID, request), ResultCode.ALERT_RULE_INVALID);
    }

    @Test
    void create_should_reject_non_owned_device() {
        AlertRuleRequest request = thresholdRequest();
        request.setDeviceId(100L);
        when(deviceService.getDeviceById(100L)).thenReturn(device(999L));

        assertCode(() -> service.create(USER_ID, request), ResultCode.DEVICE_NOT_OWNED);
    }

    @Test
    void create_should_save_valid_rule() {
        AlertRuleRequest request = thresholdRequest();

        AlertRule created = service.create(USER_ID, request);

        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getSourceType()).isEqualTo(AlertConstants.SOURCE_THRESHOLD);
        assertThat(created.getSeverity()).isEqualTo(AlertConstants.SEVERITY_WARNING);
        assertThat(created.getOperator()).isEqualTo(AlertConstants.OPERATOR_GT);
        assertThat(created.getEnabled()).isEqualTo(1);
        verify(alertRuleMapper).insert(any(AlertRule.class));
    }

    @Test
    void create_should_persist_tag_scope_ids() {
        AlertRuleRequest request = thresholdRequest();
        request.setScopeType(AlertConstants.SCOPE_TAG);
        request.setTagIds(List.of(7L, 9L));

        AlertRule created = service.create(USER_ID, request);

        assertThat(created.getScopeType()).isEqualTo(AlertConstants.SCOPE_TAG);
        assertThat(created.getDeviceId()).isNull();
        assertThat(created.getTagIds()).isEqualTo("[7,9]");
        assertThat(created.getTagIdsView()).containsExactly(7L, 9L);
    }

    @Test
    void create_should_reject_invalid_scope_with_6268() {
        doThrow(new BusinessException(ResultCode.ALERT_RULE_SCOPE_INVALID, "分组档要求且仅要求 groupIds"))
                .when(deviceScopeService).validateAndNormalize(any(), any(), eq(ResultCode.ALERT_RULE_SCOPE_INVALID));
        AlertRuleRequest request = thresholdRequest();
        request.setScopeType(AlertConstants.SCOPE_GROUP);

        assertCode(() -> service.create(USER_ID, request), ResultCode.ALERT_RULE_SCOPE_INVALID);
    }

    @Test
    void list_should_expose_scope_ids_view() {
        AlertRule rule = new AlertRule();
        rule.setId(5L);
        rule.setUserId(USER_ID);
        rule.setScopeType(AlertConstants.SCOPE_GROUP);
        rule.setGroupIds("[3,5]");
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(rule));

        List<AlertRule> result = service.list(USER_ID, null, null);

        assertThat(result.get(0).getGroupIdsView()).containsExactly(3L, 5L);
        assertThat(result.get(0).getTagIdsView()).isEmpty();
    }

    @Test
    void getOwned_should_throw_when_missing() {
        when(alertRuleMapper.selectById(5L)).thenReturn(null);

        assertCode(() -> service.getOwned(USER_ID, 5L), ResultCode.ALERT_RULE_NOT_FOUND);
    }

    @Test
    void getOwned_should_throw_when_not_owned() {
        AlertRule rule = new AlertRule();
        rule.setId(5L);
        rule.setUserId(999L);
        when(alertRuleMapper.selectById(5L)).thenReturn(rule);

        assertCode(() -> service.getOwned(USER_ID, 5L), ResultCode.FORBIDDEN);
    }

    @Test
    void list_should_return_owned_rules() {
        AlertRule rule = new AlertRule();
        rule.setId(5L);
        rule.setUserId(USER_ID);
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(rule));

        List<AlertRule> result = service.list(USER_ID, "threshold", null);

        assertThat(result).containsExactly(rule);
        verify(alertRuleMapper).selectList(any());
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private AlertRuleRequest thresholdRequest() {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("高温告警");
        request.setSourceType(AlertConstants.SOURCE_THRESHOLD);
        request.setIdentifier("temperature");
        request.setOperator(AlertConstants.OPERATOR_GT);
        request.setThresholdValue("80");
        return request;
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(100L);
        device.setOwnerId(ownerId);
        return device;
    }
}