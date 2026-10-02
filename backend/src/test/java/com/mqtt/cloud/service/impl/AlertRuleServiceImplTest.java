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
import com.mqtt.cloud.service.DeviceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警规则服务单测（T-17 实施计划 P7）。
 * <p>
 * 覆盖三类来源的配置校验（非法统一 {@code 6208}）、设备归属校验（{@code 2003}）、
 * 详情归属（不存在 {@code 6207} / 越权 {@code 403}）与列表过滤。
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private AlertRuleMapper alertRuleMapper;

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private AlertRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 需要实体的 TableInfo；纯单测无 MyBatis 上下文，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertRule.class);
        // AlertRuleServiceImpl 用显式构造器（DeviceService），Mockito 不再做字段注入，需手动装配 baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", alertRuleMapper);
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