package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.AlertProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.WebhookDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 告警通知器单测（T-17 实施计划 P7）。
 * <p>
 * 覆盖触发 / 恢复两个事件的载荷组装与投递、开关关闭不投递、设备缺失不投递、投递异常只 WARN。
 */
class AlertNotifierImplTest {

    private static final Long DEVICE_ID = 100L;

    private WebhookDispatcher webhookDispatcher;
    private AlertProperties properties;
    private AlertNotifierImpl notifier;

    @BeforeEach
    void setUp() {
        webhookDispatcher = mock(WebhookDispatcher.class);
        properties = new AlertProperties();
        notifier = new AlertNotifierImpl(webhookDispatcher, new ObjectMapper(), properties);
    }

    @Test
    void notifyTriggered_should_dispatch_payload_with_triggered_fields() {
        AlertRecord record = triggeredRecord();

        notifier.notifyTriggered(record, device());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookDispatcher).dispatch(eq(DEVICE_ID), eq(AlertConstants.EVENT_ALERT_TRIGGERED), eq(device()),
                payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("\"alertId\":12")
                .contains("\"ruleName\":\"高温告警\"")
                .contains("\"sourceType\":\"THRESHOLD\"")
                .contains("\"severity\":\"CRITICAL\"")
                .contains("\"identifier\":\"temperature\"")
                .contains("\"title\":\"温度超过 80\"")
                .contains("\"triggerValue\":\"86.5\"")
                .contains("\"triggerCount\":1");
    }

    @Test
    void notifyTriggered_should_skip_when_disabled() {
        properties.setNotifyEnabled(false);

        notifier.notifyTriggered(triggeredRecord(), device());

        verifyNoInteractions(webhookDispatcher);
    }

    @Test
    void notifyTriggered_should_skip_when_device_missing() {
        notifier.notifyTriggered(triggeredRecord(), null);

        verifyNoInteractions(webhookDispatcher);
    }

    @Test
    void notifyTriggered_should_swallow_dispatch_failure() {
        doThrow(new RuntimeException("webhook down"))
                .when(webhookDispatcher).dispatch(any(Long.class), any(), any(), any());

        assertThatCode(() -> notifier.notifyTriggered(triggeredRecord(), device()))
                .doesNotThrowAnyException();
    }

    @Test
    void notifyRecovered_should_dispatch_payload_with_recovered_fields() {
        AlertRecord record = triggeredRecord();
        record.setStatus(AlertConstants.STATUS_RECOVERED);
        record.setRecoveredAt(LocalDateTime.of(2026, 10, 2, 12, 30, 0));

        notifier.notifyRecovered(record, device());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookDispatcher).dispatch(eq(DEVICE_ID), eq(AlertConstants.EVENT_ALERT_RECOVERED), eq(device()),
                payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("\"alertId\":12")
                .contains("\"title\":\"温度超过 80\"")
                .contains("\"recoveredAt\":\"2026-10-02T12:30:00\"");
    }

    @Test
    void notifyRecovered_should_skip_when_disabled() {
        properties.setRecoverNotifyEnabled(false);

        notifier.notifyRecovered(triggeredRecord(), device());

        verifyNoInteractions(webhookDispatcher);
    }

    private AlertRecord triggeredRecord() {
        AlertRecord record = new AlertRecord();
        record.setId(12L);
        record.setUserId(1L);
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