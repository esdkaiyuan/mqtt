package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.AlertProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 告警通知器单测（T-17 实施计划 P7，T-27 P2 改造后更新）。
 * <p>
 * 对外契约不变：覆盖触发 / 恢复两个事件的载荷组装与委派、开关关闭不投递、设备缺失不投递、
 * 投递异常只 WARN。内部改为委派 {@link NotificationDispatcher}，载荷字段与 T-17 一致。
 */
class AlertNotifierImplTest {

    private static final Long DEVICE_ID = 100L;
    private static final Long USER_ID = 1L;

    private NotificationDispatcher dispatcher;
    private AlertProperties properties;
    private AlertNotifierImpl notifier;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        properties = new AlertProperties();
        notifier = new AlertNotifierImpl(dispatcher, properties);
    }

    @Test
    void notifyTriggered_should_dispatch_payload_with_triggered_fields() {
        AlertRecord record = triggeredRecord();

        notifier.notifyTriggered(record, device());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).dispatch(eq(USER_ID), eq(AlertConstants.EVENT_ALERT_TRIGGERED), eq(device()),
                eq(record), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("alertId", 12L)
                .containsEntry("ruleName", "高温告警")
                .containsEntry("sourceType", "THRESHOLD")
                .containsEntry("severity", "CRITICAL")
                .containsEntry("identifier", "temperature")
                .containsEntry("title", "温度超过 80")
                .containsEntry("triggerValue", "86.5")
                .containsEntry("triggerCount", 1);
    }

    @Test
    void notifyTriggered_should_skip_when_disabled() {
        properties.setNotifyEnabled(false);

        notifier.notifyTriggered(triggeredRecord(), device());

        verifyNoInteractions(dispatcher);
    }

    @Test
    void notifyTriggered_should_skip_when_device_missing() {
        notifier.notifyTriggered(triggeredRecord(), null);

        verifyNoInteractions(dispatcher);
    }

    @Test
    void notifyTriggered_should_swallow_dispatch_failure() {
        doThrow(new RuntimeException("notification down"))
                .when(dispatcher).dispatch(any(), any(), any(), any(), anyMap());

        assertThatCode(() -> notifier.notifyTriggered(triggeredRecord(), device()))
                .doesNotThrowAnyException();
    }

    @Test
    void notifyRecovered_should_dispatch_payload_with_recovered_fields() {
        AlertRecord record = triggeredRecord();
        record.setStatus(AlertConstants.STATUS_RECOVERED);
        record.setRecoveredAt(LocalDateTime.of(2026, 10, 2, 12, 30, 0));

        notifier.notifyRecovered(record, device());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).dispatch(eq(USER_ID), eq(AlertConstants.EVENT_ALERT_RECOVERED), eq(device()),
                eq(record), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("alertId", 12L)
                .containsEntry("title", "温度超过 80")
                .containsEntry("recoveredAt", "2026-10-02T12:30:00");
    }

    @Test
    void notifyRecovered_should_skip_when_disabled() {
        properties.setRecoverNotifyEnabled(false);

        notifier.notifyRecovered(triggeredRecord(), device());

        verifyNoInteractions(dispatcher);
    }

    private AlertRecord triggeredRecord() {
        AlertRecord record = new AlertRecord();
        record.setId(12L);
        record.setUserId(USER_ID);
        record.setDeviceId(DEVICE_ID);
        record.setDeviceKey("dev-1");
        record.setRuleId(3L);
        record.setRuleName("高温告警");
        record.setSourceType(AlertConstants.SOURCE_THRESHOLD);
        record.setSeverity(AlertConstants.SEVERITY_CRITICAL);
        record.setIdentifier("temperature");
        record.setTitle("温度超过 80");
        record.setTriggerValue("86.5");
        record.setStatus(AlertConstants.STATUS_TRIGGERED);
        record.setTriggerCount(1);
        record.setFirstTriggeredAt(LocalDateTime.of(2026, 10, 2, 10, 0, 0));
        record.setLastTriggeredAt(LocalDateTime.of(2026, 10, 2, 10, 0, 0));
        return record;
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey("dev-1");
        device.setDeviceName("1号温控器");
        return device;
    }
}