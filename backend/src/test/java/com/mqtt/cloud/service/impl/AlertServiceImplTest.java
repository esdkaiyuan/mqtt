package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.AlertNotifier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警记录服务单测（T-17 实施计划 P7）。
 * <p>
 * 覆盖分页参数收敛、详情归属（不存在 {@code 6209} / 越权 {@code 403}）、
 * 状态机守卫（确认 / 恢复非法状态 {@code 6210}）、人工恢复后通知、未读数与最近列表。
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long ALERT_ID = 5L;
    private static final Long DEVICE_ID = 100L;

    @Mock
    private AlertRecordMapper alertRecordMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlertNotifier alertNotifier;

    @InjectMocks
    private AlertServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertRecord.class);
        ReflectionTestUtils.setField(service, "baseMapper", alertRecordMapper);
    }

    @Test
    void page_should_clamp_page_size_to_max() {
        when(alertRecordMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        AlertQuery query = new AlertQuery();
        query.setPageSize(999);

        IPage<AlertRecord> page = service.page(USER_ID, query);

        assertThat(page.getSize()).isEqualTo(100);
    }

    @Test
    void page_should_use_defaults_when_query_null() {
        when(alertRecordMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        IPage<AlertRecord> page = service.page(USER_ID, null);

        assertThat(page.getSize()).isEqualTo(10);
    }

    @Test
    void page_should_exclude_recovered_when_open_only() {
        when(alertRecordMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        AlertQuery query = new AlertQuery();
        query.setOpenOnly(true);

        service.page(USER_ID, query);

        assertThat(capturedWrapperParams()).containsValue(AlertConstants.STATUS_RECOVERED);
    }

    @Test
    void page_should_prefer_status_over_open_only() {
        when(alertRecordMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        AlertQuery query = new AlertQuery();
        query.setStatus("TRIGGERED");
        query.setOpenOnly(true);

        service.page(USER_ID, query);

        assertThat(capturedWrapperParams())
                .containsValue(AlertConstants.STATUS_TRIGGERED)
                .doesNotContainValue(AlertConstants.STATUS_RECOVERED);
    }

    private Map<String, Object> capturedWrapperParams() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AlertRecord>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alertRecordMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper<AlertRecord> wrapper = captor.getValue();
        // 条件值以 ISqlSegment 惰性求值，需先渲染 SQL 片段才会写入 paramNameValuePairs
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs();
    }

    @Test
    void getOwned_should_throw_when_id_null() {
        assertCode(() -> service.getOwned(USER_ID, null), ResultCode.ALERT_NOT_FOUND);
    }

    @Test
    void getOwned_should_throw_when_missing() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(null);

        assertCode(() -> service.getOwned(USER_ID, ALERT_ID), ResultCode.ALERT_NOT_FOUND);
    }

    @Test
    void getOwned_should_throw_when_not_owned() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record(999L));

        assertCode(() -> service.getOwned(USER_ID, ALERT_ID), ResultCode.FORBIDDEN);
    }

    @Test
    void acknowledge_should_succeed_when_triggered() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record(USER_ID));
        when(alertRecordMapper.markAcknowledged(eq(ALERT_ID), any(), eq(USER_ID))).thenReturn(1);

        service.acknowledge(USER_ID, ALERT_ID);

        verify(alertRecordMapper).markAcknowledged(eq(ALERT_ID), any(), eq(USER_ID));
    }

    @Test
    void acknowledge_should_throw_when_state_not_triggered() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record(USER_ID));
        when(alertRecordMapper.markAcknowledged(eq(ALERT_ID), any(), eq(USER_ID))).thenReturn(0);

        assertCode(() -> service.acknowledge(USER_ID, ALERT_ID), ResultCode.ALERT_STATUS_INVALID);
    }

    @Test
    void recover_should_mark_and_notify() {
        AlertRecord record = record(USER_ID);
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record);
        when(alertRecordMapper.markRecovered(eq(ALERT_ID), any())).thenReturn(1);
        Device device = new Device();
        device.setId(DEVICE_ID);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device);

        service.recover(USER_ID, ALERT_ID);

        assertThat(record.getStatus()).isEqualTo(AlertConstants.STATUS_RECOVERED);
        verify(alertNotifier).notifyRecovered(record, device);
    }

    @Test
    void recover_should_throw_when_already_recovered() {
        when(alertRecordMapper.selectById(ALERT_ID)).thenReturn(record(USER_ID));
        when(alertRecordMapper.markRecovered(eq(ALERT_ID), any())).thenReturn(0);

        assertCode(() -> service.recover(USER_ID, ALERT_ID), ResultCode.ALERT_STATUS_INVALID);
    }

    @Test
    void unreadCount_should_delegate() {
        when(alertRecordMapper.countOpenByUser(USER_ID)).thenReturn(3L);

        assertThat(service.unreadCount(USER_ID)).isEqualTo(3L);
    }

    @Test
    void recent_should_clamp_limit() {
        service.recent(USER_ID, 0);

        verify(alertRecordMapper).selectOpenByUserLimit(USER_ID, 10);
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
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
}