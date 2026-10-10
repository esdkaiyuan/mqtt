package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.AlertEscalationPolicy;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.AlertEscalationService;
import com.mqtt.cloud.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 告警升级巡检服务单测（T-27 实施计划 P4）。
 * <p>
 * 覆盖四条核心语义：级别过滤（策略 {@code severity} 透传到候选查询）、阈值判定
 * （{@code threshold = now - after_seconds}）、条件更新去重（受影响行数为 0 视为已被并发升级）、
 * 达上限不再升级（只刷新锚点、不通知）；另覆盖渠道定向与异常隔离。
 */
class AlertEscalationSweeperServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long ALERT_ID = 5L;
    private static final Long DEVICE_ID = 100L;
    private static final Long RULE_ID = 20L;
    private static final int BATCH = 200;

    private AlertEscalationService escalationService;
    private AlertRecordMapper alertRecordMapper;
    private DeviceMapper deviceMapper;
    private NotificationDispatcher notificationDispatcher;
    private NotificationProperties properties;
    private AlertEscalationSweeperServiceImpl service;

    @BeforeEach
    void setUp() {
        escalationService = mock(AlertEscalationService.class);
        alertRecordMapper = mock(AlertRecordMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        notificationDispatcher = mock(NotificationDispatcher.class);
        properties = new NotificationProperties();
        service = new AlertEscalationSweeperServiceImpl(escalationService, alertRecordMapper, deviceMapper,
                notificationDispatcher, properties, new ObjectMapper());
    }

    @Test
    void sweep_should_escalate_and_dispatch_to_policy_channels() {
        AlertEscalationPolicy policy = policy(AlertConstants.SEVERITY_CRITICAL, 300);
        policy.setAssigneeId(7L);
        policy.setChannelIds("[9]");
        AlertRecord record = candidate(0, AlertConstants.SEVERITY_CRITICAL);
        when(escalationService.listEnabled(BATCH)).thenReturn(List.of(policy));
        when(alertRecordMapper.selectEscalationCandidates(eq(USER_ID), eq("CRITICAL"), any(), eq(BATCH)))
                .thenReturn(List.of(record));
        when(alertRecordMapper.escalate(eq(ALERT_ID), eq(0), eq(1), eq(7L), any())).thenReturn(1);
        Device device = device();
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device);

        service.sweep();

        assertThat(record.getEscalationLevel()).isEqualTo(1);
        assertThat(record.getAssigneeId()).isEqualTo(7L);
        assertThat(record.getLastEscalatedAt()).isNotNull();
        verify(notificationDispatcher).dispatch(eq(USER_ID), eq(AlertConstants.EVENT_ALERT_ESCALATED),
                eq(device), eq(record), any(), eq(Set.of(9L)));
    }

    @Test
    void sweep_should_pass_policy_severity_to_candidate_query() {
        AlertEscalationPolicy policy = policy(AlertConstants.SEVERITY_WARNING, 120);
        when(escalationService.listEnabled(BATCH)).thenReturn(List.of(policy));
        when(alertRecordMapper.selectEscalationCandidates(any(), any(), any(), anyInt())).thenReturn(List.of());

        service.sweep();

        verify(alertRecordMapper).selectEscalationCandidates(eq(USER_ID), eq("WARNING"), any(), eq(BATCH));
    }

    @Test
    void sweep_should_compute_threshold_from_after_seconds() {
        AlertEscalationPolicy policy = policy(AlertConstants.SEVERITY_WARNING, 300);
        when(escalationService.listEnabled(BATCH)).thenReturn(List.of(policy));
        when(alertRecordMapper.selectEscalationCandidates(any(), any(), any(), anyInt())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusSeconds(300);
        service.sweep();
        LocalDateTime after = LocalDateTime.now().minusSeconds(300);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRecordMapper).selectEscalationCandidates(eq(USER_ID), eq("WARNING"), captor.capture(), eq(BATCH));
        assertThat(captor.getValue()).isBetween(before, after);
    }

    @Test
    void sweep_should_skip_when_conditional_update_misses() {
        AlertEscalationPolicy policy = policy(AlertConstants.SEVERITY_CRITICAL, 300);
        AlertRecord record = candidate(1, AlertConstants.SEVERITY_CRITICAL);
        when(escalationService.listEnabled(BATCH)).thenReturn(List.of(policy));
        when(alertRecordMapper.selectEscalationCandidates(eq(USER_ID), eq("CRITICAL"), any(), eq(BATCH)))
                .thenReturn(List.of(record));
        when(alertRecordMapper.escalate(eq(ALERT_ID), eq(1), eq(2), any(), any())).thenReturn(0);

        service.sweep();

        assertThat(record.getEscalationLevel()).isEqualTo(1);
        verifyNoInteractions(notificationDispatcher);
        verify(deviceMapper, never()).selectById(any());
    }

    @Test
    void sweep_should_not_escalate_beyond_max_level() {
        properties.setMaxEscalationLevel(3);
        AlertEscalationPolicy policy = policy(AlertConstants.SEVERITY_CRITICAL, 300);
        AlertRecord record = candidate(3, AlertConstants.SEVERITY_CRITICAL);
        when(escalationService.listEnabled(BATCH)).thenReturn(List.of(policy));
        when(alertRecordMapper.selectEscalationCandidates(eq(USER_ID), eq("CRITICAL"), any(), eq(BATCH)))
                .thenReturn(List.of(record));
        when(alertRecordMapper.escalate(eq(ALERT_ID), eq(3), eq(3), isNull(), any())).thenReturn(1);

        service.sweep();

        assertThat(record.getEscalationLevel()).isEqualTo(3);
        verify(alertRecordMapper).escalate(eq(ALERT_ID), eq(3), eq(3), isNull(), any());
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void sweep_should_dispatch_without_channel_restriction_when_channel_ids_blank() {
        AlertEscalationPolicy policy = policy(AlertConstants.SEVERITY_INFO, 300);
        AlertRecord record = candidate(0, AlertConstants.SEVERITY_INFO);
        when(escalationService.listEnabled(BATCH)).thenReturn(List.of(policy));
        when(alertRecordMapper.selectEscalationCandidates(eq(USER_ID), eq("INFO"), any(), eq(BATCH)))
                .thenReturn(List.of(record));
        when(alertRecordMapper.escalate(eq(ALERT_ID), eq(0), eq(1), isNull(), any())).thenReturn(1);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());

        service.sweep();

        verify(notificationDispatcher).dispatch(eq(USER_ID), eq(AlertConstants.EVENT_ALERT_ESCALATED),
                any(), eq(record), any(), isNull());
    }

    @Test
    void sweep_should_isolate_candidate_query_failure() {
        AlertEscalationPolicy policy = policy(AlertConstants.SEVERITY_CRITICAL, 300);
        when(escalationService.listEnabled(BATCH)).thenReturn(List.of(policy));
        when(alertRecordMapper.selectEscalationCandidates(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.sweep()).doesNotThrowAnyException();
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void sweep_should_isolate_policy_load_failure() {
        when(escalationService.listEnabled(BATCH)).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.sweep()).doesNotThrowAnyException();
        verifyNoInteractions(alertRecordMapper);
    }

    private AlertEscalationPolicy policy(String severity, int afterSeconds) {
        AlertEscalationPolicy policy = new AlertEscalationPolicy();
        policy.setId(1L);
        policy.setUserId(USER_ID);
        policy.setName("升级策略");
        policy.setSeverity(severity);
        policy.setAfterSeconds(afterSeconds);
        policy.setEnabled(1);
        return policy;
    }

    private AlertRecord candidate(int level, String severity) {
        AlertRecord record = new AlertRecord();
        record.setId(ALERT_ID);
        record.setUserId(USER_ID);
        record.setDeviceId(DEVICE_ID);
        record.setRuleId(RULE_ID);
        record.setSeverity(severity);
        record.setTitle("温度过高");
        record.setStatus(AlertConstants.STATUS_TRIGGERED);
        record.setEscalationLevel(level);
        return record;
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey("dev-1");
        device.setOwnerId(USER_ID);
        return device;
    }
}