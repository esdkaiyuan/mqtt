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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 离线巡检服务单测（T-17 实施计划 P7）。
 * <p>
 * 覆盖离线超阈值触发 / 未达阈值不触发 / 设备重新上线自动恢复 / 规则删除兜底恢复 /
 * 规则停用或删除不参与评估 / 总开关关闭。巡检只负责挑出 (规则, 设备)，状态流转由评估服务完成，
 * 故断言集中在「是否按预期调用 {@code evaluateOffline} / {@code recover}」。
 */
class AlertSweeperServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long DEVICE_ID = 100L;
    private static final Long RULE_ID = 20L;
    private static final int BATCH = 200;

    private AlertRuleMapper alertRuleMapper;
    private AlertRecordMapper alertRecordMapper;
    private DeviceMapper deviceMapper;
    private AlertEvaluationService alertEvaluationService;
    private AlertProperties properties;
    private AlertSweeperServiceImpl service;

    @BeforeEach
    void setUp() {
        alertRuleMapper = mock(AlertRuleMapper.class);
        alertRecordMapper = mock(AlertRecordMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        alertEvaluationService = mock(AlertEvaluationService.class);
        properties = new AlertProperties();
        service = new AlertSweeperServiceImpl(alertRuleMapper, alertRecordMapper, deviceMapper,
                alertEvaluationService, properties);
    }

    @Test
    void sweep_should_evaluate_offline_devices_past_threshold() {
        AlertRule rule = offlineRule(600);
        when(alertRuleMapper.selectEnabledBySource(AlertConstants.SOURCE_OFFLINE, BATCH)).thenReturn(List.of(rule));
        when(deviceMapper.findOfflineDevicesForAlert(USER_ID, null, 600, BATCH)).thenReturn(List.of(device()));

        service.sweep();

        verify(alertEvaluationService).evaluateOffline(rule, device());
    }

    @Test
    void sweep_should_not_evaluate_when_no_device_reached_threshold() {
        AlertRule rule = offlineRule(600);
        when(alertRuleMapper.selectEnabledBySource(AlertConstants.SOURCE_OFFLINE, BATCH)).thenReturn(List.of(rule));
        when(deviceMapper.findOfflineDevicesForAlert(USER_ID, null, 600, BATCH)).thenReturn(List.of());

        service.sweep();

        verify(alertEvaluationService, never()).evaluateOffline(any(), any());
    }

    @Test
    void sweep_should_recover_when_device_back_online() {
        AlertRule rule = offlineRule(600);
        AlertRecord open = openRecord();
        when(alertRuleMapper.selectEnabledBySource(AlertConstants.SOURCE_OFFLINE, BATCH)).thenReturn(List.of(rule));
        when(deviceMapper.findOfflineDevicesForAlert(USER_ID, null, 600, BATCH)).thenReturn(List.of());
        when(deviceMapper.findOnlineDevices(USER_ID)).thenReturn(List.of(device()));
        when(alertRecordMapper.selectOpenByRuleAndDevices(RULE_ID, List.of(DEVICE_ID))).thenReturn(List.of(open));

        service.sweep();

        verify(alertEvaluationService).recover(open, device());
    }

    @Test
    void sweep_should_recover_alerts_of_deleted_rule() {
        AlertRecord open = openRecord();
        when(alertRuleMapper.selectEnabledBySource(AlertConstants.SOURCE_OFFLINE, BATCH)).thenReturn(List.of());
        when(alertRecordMapper.selectOpenByDeletedRule(BATCH)).thenReturn(List.of(open));
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());

        service.sweep();

        verify(alertEvaluationService).recover(open, device());
    }

    @Test
    void sweep_should_not_evaluate_disabled_or_deleted_rule() {
        // 停用 / 逻辑删除的规则不会被 selectEnabledBySource 命中，故不参与评估
        when(alertRuleMapper.selectEnabledBySource(AlertConstants.SOURCE_OFFLINE, BATCH)).thenReturn(List.of());

        service.sweep();

        verify(alertEvaluationService, never()).evaluateOffline(any(), any());
        verify(deviceMapper, never()).findOfflineDevicesForAlert(any(), any(), anyInt(), anyInt());
    }

    @Test
    void sweep_should_noop_when_disabled() {
        properties.setEnabled(false);

        service.sweep();

        verifyNoInteractions(alertRuleMapper, alertRecordMapper, deviceMapper, alertEvaluationService);
    }

    @Test
    void sweep_should_isolate_single_rule_failure() {
        AlertRule broken = offlineRule(600);
        AlertRule healthy = offlineRule(600);
        healthy.setId(21L);
        when(alertRuleMapper.selectEnabledBySource(AlertConstants.SOURCE_OFFLINE, BATCH))
                .thenReturn(List.of(broken, healthy));
        when(deviceMapper.findOfflineDevicesForAlert(USER_ID, null, 600, BATCH))
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(List.of(device()));

        service.sweep();

        verify(alertEvaluationService).evaluateOffline(eq(healthy), eq(device()));
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey("dev-1");
        device.setOwnerId(USER_ID);
        return device;
    }

    private AlertRule offlineRule(int offlineSeconds) {
        AlertRule rule = new AlertRule();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setName("离线告警");
        rule.setSourceType(AlertConstants.SOURCE_OFFLINE);
        rule.setSeverity(AlertConstants.SEVERITY_WARNING);
        rule.setOfflineSeconds(offlineSeconds);
        rule.setSuppressWindowSeconds(0);
        rule.setEnabled(1);
        return rule;
    }

    private AlertRecord openRecord() {
        AlertRecord record = new AlertRecord();
        record.setId(5L);
        record.setUserId(USER_ID);
        record.setDeviceId(DEVICE_ID);
        record.setRuleId(RULE_ID);
        record.setStatus(AlertConstants.STATUS_TRIGGERED);
        record.setTriggerCount(1);
        return record;
    }
}