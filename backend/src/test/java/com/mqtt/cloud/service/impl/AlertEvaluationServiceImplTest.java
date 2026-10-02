package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.AlertProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.AlertRuleMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.AlertEvaluationService;
import com.mqtt.cloud.service.AlertEvaluationService.EventSample;
import com.mqtt.cloud.service.AlertEvaluationService.PropertySample;
import com.mqtt.cloud.service.AlertNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 告警评估服务单测（T-17 实施计划 P7）。
 * <p>
 * 覆盖阈值越限 / 边界（{@code GTE} 等于触发、{@code GT} 等于不触发）/ 回落恢复 /
 * 窗口内去重（计数累加、回传原通知时间、不重复通知）/ 窗口外再次通知 / 事件命中与去重 /
 * {@code NE} 与枚举文本比较 / 不可解析值跳过 / 总开关关闭 / 异常隔离 / 离线触发。
 */
class AlertEvaluationServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long DEVICE_ID = 100L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long RULE_ID = 10L;
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 2, 12, 0, 0);
    private static final LocalDateTime NOTIFIED_AT = LocalDateTime.of(2026, 10, 2, 11, 0, 0);

    private AlertRuleMapper alertRuleMapper;
    private AlertRecordMapper alertRecordMapper;
    private DeviceMapper deviceMapper;
    private AlertNotifier alertNotifier;
    private AlertProperties properties;
    private AlertEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        alertRuleMapper = mock(AlertRuleMapper.class);
        alertRecordMapper = mock(AlertRecordMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        alertNotifier = mock(AlertNotifier.class);
        properties = new AlertProperties();
        service = new AlertEvaluationServiceImpl(alertRuleMapper, alertRecordMapper, deviceMapper,
                alertNotifier, properties);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());
    }

    // ---------- 阈值 ----------

    @Test
    void onProperties_should_create_record_and_notify_when_threshold_exceeded() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_GT, "80");
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(null);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "86.5", RECEIVED_AT)));

        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        AlertRecord saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AlertConstants.STATUS_TRIGGERED);
        assertThat(saved.getTriggerCount()).isEqualTo(1);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getDeviceKey()).isEqualTo("dev-1");
        assertThat(saved.getRuleName()).isEqualTo("高温告警");
        assertThat(saved.getSeverity()).isEqualTo(AlertConstants.SEVERITY_CRITICAL);
        assertThat(saved.getTitle()).isEqualTo("temperature 超过 80");
        assertThat(saved.getTriggerValue()).isEqualTo("86.5");
        assertThat(saved.getFirstTriggeredAt()).isNotNull();
        assertThat(saved.getLastTriggeredAt()).isNotNull();
        verify(alertNotifier).notifyTriggered(saved, device());
    }

    @Test
    void onProperties_should_trigger_on_boundary_for_gte() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_GTE, "80");
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(null);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "80", RECEIVED_AT)));

        verify(alertRecordMapper).insert(any(AlertRecord.class));
    }

    @Test
    void onProperties_should_not_trigger_on_boundary_for_gt() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_GT, "80");
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(null);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "80", RECEIVED_AT)));

        verify(alertRecordMapper, never()).insert(any(AlertRecord.class));
        verify(alertNotifier, never()).notifyTriggered(any(), any());
    }

    @Test
    void onProperties_should_recover_when_value_falls_back() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_GT, "80");
        AlertRecord open = openRecord(5L, LocalDateTime.now().minusSeconds(30), NOTIFIED_AT);
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(open);
        when(alertRecordMapper.markRecovered(eq(5L), any(LocalDateTime.class))).thenReturn(1);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "50", RECEIVED_AT)));

        verify(alertRecordMapper).markRecovered(eq(5L), any(LocalDateTime.class));
        verify(alertNotifier).notifyRecovered(eq(open), eq(device()));
        verify(alertRecordMapper, never()).insert(any(AlertRecord.class));
    }

    @Test
    void onProperties_should_dedupe_within_window_without_repeat_notify() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_GT, "80");
        AlertRecord open = openRecord(5L, LocalDateTime.now().minusSeconds(10), NOTIFIED_AT);
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(open);
        when(alertRecordMapper.updateTrigger(anyLong(), anyInt(), any(), any(), any())).thenReturn(1);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "86.5", RECEIVED_AT)));

        ArgumentCaptor<LocalDateTime> notifiedCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRecordMapper).updateTrigger(eq(5L), eq(2), any(LocalDateTime.class), eq("86.5"),
                notifiedCaptor.capture());
        // 窗口内去重必须回传原通知时间，避免 updateTrigger 无条件覆写 notified_at
        assertThat(notifiedCaptor.getValue()).isEqualTo(NOTIFIED_AT);
        verify(alertNotifier, never()).notifyTriggered(any(), any());
        verify(alertRecordMapper, never()).insert(any(AlertRecord.class));
    }

    @Test
    void onProperties_should_notify_again_when_window_elapsed() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_GT, "80");
        AlertRecord open = openRecord(5L, LocalDateTime.now().minusSeconds(400), NOTIFIED_AT);
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(open);
        when(alertRecordMapper.updateTrigger(anyLong(), anyInt(), any(), any(), any())).thenReturn(1);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "86.5", RECEIVED_AT)));

        ArgumentCaptor<LocalDateTime> notifiedCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRecordMapper).updateTrigger(eq(5L), eq(2), any(LocalDateTime.class), eq("86.5"),
                notifiedCaptor.capture());
        assertThat(notifiedCaptor.getValue()).isAfter(NOTIFIED_AT);
        verify(alertNotifier).notifyTriggered(eq(open), eq(device()));
    }

    @Test
    void onProperties_should_skip_when_value_not_parseable() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_GT, "80");
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "warm", RECEIVED_AT)));

        verify(alertRecordMapper, never()).insert(any(AlertRecord.class));
        verify(alertRecordMapper, never()).markRecovered(anyLong(), any());
        verify(alertNotifier, never()).notifyTriggered(any(), any());
    }

    @Test
    void onProperties_should_compare_enum_by_text_for_ne() {
        AlertRule rule = thresholdRule(AlertConstants.OPERATOR_NE, "auto");
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(null);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "manual", RECEIVED_AT)));

        verify(alertRecordMapper).insert(any(AlertRecord.class));
    }

    @Test
    void onProperties_should_noop_when_disabled() {
        properties.setEnabled(false);

        service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "86.5", RECEIVED_AT)));

        verifyNoInteractions(alertRuleMapper, alertRecordMapper, deviceMapper, alertNotifier);
    }

    @Test
    void onProperties_should_swallow_exception() {
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_THRESHOLD))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "86.5", RECEIVED_AT))))
                .doesNotThrowAnyException();
    }

    // ---------- 事件 ----------

    @Test
    void onEvents_should_create_record_and_notify_when_rule_matched() {
        AlertRule rule = eventRule();
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_EVENT))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(null);

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "alert", "{\"level\":3}", RECEIVED_AT)));

        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("事件 fall");
        assertThat(captor.getValue().getTriggerValue()).isEqualTo("{\"level\":3}");
        verify(alertNotifier).notifyTriggered(captor.getValue(), device());
    }

    @Test
    void onEvents_should_skip_when_event_type_mismatched() {
        AlertRule rule = eventRule();
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_EVENT))
                .thenReturn(List.of(rule));

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "info", null, RECEIVED_AT)));

        verify(alertRecordMapper, never()).insert(any(AlertRecord.class));
    }

    @Test
    void onEvents_should_dedupe_within_window() {
        AlertRule rule = eventRule();
        AlertRecord open = openRecord(5L, LocalDateTime.now().minusSeconds(5), NOTIFIED_AT);
        when(alertRuleMapper.selectEnabledForDevice(USER_ID, DEVICE_ID, AlertConstants.SOURCE_EVENT))
                .thenReturn(List.of(rule));
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(open);
        when(alertRecordMapper.updateTrigger(anyLong(), anyInt(), any(), any(), any())).thenReturn(1);

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "alert", null, RECEIVED_AT)));

        verify(alertRecordMapper).updateTrigger(eq(5L), eq(2), any(LocalDateTime.class), any(), eq(NOTIFIED_AT));
        verify(alertNotifier, never()).notifyTriggered(any(), any());
    }

    // ---------- 离线 ----------

    @Test
    void evaluateOffline_should_trigger_with_offline_title() {
        AlertRule rule = offlineRule(600);
        when(alertRecordMapper.selectOpenByRuleAndDevice(RULE_ID, DEVICE_ID)).thenReturn(null);

        service.evaluateOffline(rule, device());

        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("设备离线超过 600 秒");
        assertThat(captor.getValue().getTriggerValue()).isNull();
        verify(alertNotifier).notifyTriggered(captor.getValue(), device());
    }

    // ---------- helpers ----------

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey("dev-1");
        device.setOwnerId(USER_ID);
        device.setProductId(PRODUCT_ID);
        return device;
    }

    private AlertRule thresholdRule(String operator, String thresholdValue) {
        AlertRule rule = baseRule(AlertConstants.SOURCE_THRESHOLD);
        rule.setIdentifier("temperature");
        rule.setOperator(operator);
        rule.setThresholdValue(thresholdValue);
        return rule;
    }

    private AlertRule eventRule() {
        AlertRule rule = baseRule(AlertConstants.SOURCE_EVENT);
        rule.setIdentifier("fall");
        rule.setEventType("alert");
        return rule;
    }

    private AlertRule offlineRule(int offlineSeconds) {
        AlertRule rule = baseRule(AlertConstants.SOURCE_OFFLINE);
        rule.setOfflineSeconds(offlineSeconds);
        return rule;
    }

    private AlertRule baseRule(String sourceType) {
        AlertRule rule = new AlertRule();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setName("高温告警");
        rule.setSourceType(sourceType);
        rule.setSeverity(AlertConstants.SEVERITY_CRITICAL);
        rule.setSuppressWindowSeconds(0);
        rule.setEnabled(1);
        return rule;
    }

    private AlertRecord openRecord(Long id, LocalDateTime lastTriggeredAt, LocalDateTime notifiedAt) {
        AlertRecord record = new AlertRecord();
        record.setId(id);
        record.setUserId(USER_ID);
        record.setDeviceId(DEVICE_ID);
        record.setRuleId(RULE_ID);
        record.setStatus(AlertConstants.STATUS_TRIGGERED);
        record.setTriggerCount(1);
        record.setLastTriggeredAt(lastTriggeredAt);
        record.setNotifiedAt(notifiedAt);
        return record;
    }
}