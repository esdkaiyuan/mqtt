package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.AlertAssignRequest;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.UserMapper;
import com.mqtt.cloud.service.AlertNotifier;
import com.mqtt.cloud.service.NotificationDispatcher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 告警手动指派服务单测（T-27 实施计划 P4）。
 * <p>
 * 覆盖指派、解除指派、越权与非法处理人四条，并覆盖 {@code notify=true} 时投递一次
 * {@code alert.escalated}、条件更新未命中抛 {@code 6209}。
 */
@ExtendWith(MockitoExtension.class)
class AlertAssignTest {

    private static final Long USER_ID = 10L;
    private static final Long ALERT_ID = 5L;
    private static final Long ASSIGNEE_ID = 77L;
    private static final Long DEVICE_ID = 100L;

    @Mock
    private AlertRecordMapper alertRecordMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AlertNotifier alertNotifier;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private AlertServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertRecord.class);
        service = new AlertServiceImpl(deviceMapper, userMapper, alertNotifier, notificationDispatcher);
        ReflectionTestUtils.setField(service, "baseMapper", alertRecordMapper);
    }

    @Test
    void assign_should_set_assignee() {
        AlertRecord record = record(USER_ID);
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record);
        when(userMapper.selectById(ASSIGNEE_ID)).thenReturn(user(ASSIGNEE_ID));
        when(alertRecordMapper.assign(eq(ALERT_ID), eq(USER_ID), eq(ASSIGNEE_ID), any())).thenReturn(1);
        AlertAssignRequest request = request(ASSIGNEE_ID, false);

        service.assign(USER_ID, ALERT_ID, request);

        assertThat(record.getAssigneeId()).isEqualTo(ASSIGNEE_ID);
        verify(alertRecordMapper).assign(eq(ALERT_ID), eq(USER_ID), eq(ASSIGNEE_ID), any());
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void assign_should_clear_assignee_when_null() {
        AlertRecord record = record(USER_ID);
        record.setAssigneeId(ASSIGNEE_ID);
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record);
        when(alertRecordMapper.assign(eq(ALERT_ID), eq(USER_ID), isNull(), any())).thenReturn(1);

        service.assign(USER_ID, ALERT_ID, new AlertAssignRequest());

        assertThat(record.getAssigneeId()).isNull();
        verify(userMapper, never()).selectById(any());
        verify(alertRecordMapper).assign(eq(ALERT_ID), eq(USER_ID), isNull(), any());
    }

    @Test
    void assign_should_reject_when_not_owned() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record(999L));

        assertCode(() -> service.assign(USER_ID, ALERT_ID, request(ASSIGNEE_ID, false)), ResultCode.FORBIDDEN);
        verify(alertRecordMapper, never()).assign(any(), any(), any(), any());
    }

    @Test
    void assign_should_reject_unknown_assignee() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record(USER_ID));
        when(userMapper.selectById(ASSIGNEE_ID)).thenReturn(null);

        assertCode(() -> service.assign(USER_ID, ALERT_ID, request(ASSIGNEE_ID, false)),
                ResultCode.ALERT_ASSIGNEE_INVALID);
        verify(alertRecordMapper, never()).assign(any(), any(), any(), any());
    }

    @Test
    void assign_should_throw_when_update_missed() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record(USER_ID));
        when(userMapper.selectById(ASSIGNEE_ID)).thenReturn(user(ASSIGNEE_ID));
        when(alertRecordMapper.assign(any(), any(), any(), any())).thenReturn(0);

        assertCode(() -> service.assign(USER_ID, ALERT_ID, request(ASSIGNEE_ID, false)), ResultCode.ALERT_NOT_FOUND);
    }

    @Test
    void assign_should_dispatch_escalation_when_notify() {
        AlertRecord record = record(USER_ID);
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record);
        when(userMapper.selectById(ASSIGNEE_ID)).thenReturn(user(ASSIGNEE_ID));
        when(alertRecordMapper.assign(eq(ALERT_ID), eq(USER_ID), eq(ASSIGNEE_ID), any())).thenReturn(1);
        Device device = new Device();
        device.setId(DEVICE_ID);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device);

        service.assign(USER_ID, ALERT_ID, request(ASSIGNEE_ID, true));

        verify(notificationDispatcher).dispatch(eq(USER_ID), eq(AlertConstants.EVENT_ALERT_ESCALATED),
                eq(device), eq(record), any());
    }

    private AlertAssignRequest request(Long assigneeId, boolean notify) {
        AlertAssignRequest request = new AlertAssignRequest();
        request.setAssigneeId(assigneeId);
        request.setNotify(notify);
        return request;
    }

    private AlertRecord record(Long userId) {
        AlertRecord record = new AlertRecord();
        record.setId(ALERT_ID);
        record.setUserId(userId);
        record.setDeviceId(DEVICE_ID);
        record.setStatus(AlertConstants.STATUS_TRIGGERED);
        record.setTriggerCount(1);
        return record;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }
}