package com.mqtt.cloud.service;

import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationChannel;
import com.mqtt.cloud.service.notification.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通知统一分发器单测（T-27 实施计划 P2 / P3）。
 * <p>
 * 覆盖核心语义：事件匹配、逐渠道隔离、站内信必达、未匹配不写记录、配置型渠道逐行投递；
 * 并校验 {@code app.notification.enabled=false} 时内置 Webhook 通道不受影响。
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Long USER_ID = 10L;
    private static final String EVENT_TRIGGERED = "alert.triggered";

    @Mock
    private NotificationDeliveryService deliveryService;

    @Mock
    private NotificationChannelConfigService configService;

    @Mock
    private ObjectMapper objectMapper;

    private NotificationProperties properties;
    private NotificationChannel inapp;
    private NotificationChannel webhook;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        inapp = channel("INAPP");
        webhook = channel("WEBHOOK");
        // 故意乱序传入，验证分发器内部按固定顺序组织；直通执行器让异步投递在断言前完成
        dispatcher = new NotificationDispatcher(List.of(webhook, inapp), properties, deliveryService,
                configService, objectMapper, Runnable::run);
    }

    @Test
    void dispatch_should_deliver_inapp_and_record_success() {
        when(inapp.send(any())).thenReturn(ChannelSendResult.success(null));

        dispatcher.dispatch(USER_ID, EVENT_TRIGGERED, device(), record(), Map.of("alertId", 5L));

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(inapp).send(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getEventType()).isEqualTo(EVENT_TRIGGERED);
        assertThat(captor.getValue().getRefId()).isEqualTo(5L);
        verify(deliveryService).record(any(), eq("INAPP"), isNull(), any());
    }

    @Test
    void dispatch_should_isolate_channel_failure() {
        when(inapp.send(any())).thenThrow(new RuntimeException("inapp down"));
        when(webhook.send(any())).thenReturn(ChannelSendResult.success("example.com"));

        assertThatCode(() -> dispatcher.dispatch(USER_ID, EVENT_TRIGGERED, device(), record(), Map.of()))
                .doesNotThrowAnyException();

        verify(webhook).send(any());
        ArgumentCaptor<ChannelSendResult> captor = ArgumentCaptor.forClass(ChannelSendResult.class);
        verify(deliveryService).record(any(), eq("INAPP"), isNull(), captor.capture());
        assertThat(captor.getValue().success()).isFalse();
        assertThat(captor.getValue().errorMessage()).isEqualTo("inapp down");
        verify(deliveryService).record(any(), eq("WEBHOOK"), isNull(), any());
    }

    @Test
    void dispatch_should_not_record_when_channel_skips() {
        when(inapp.send(any())).thenReturn(ChannelSendResult.skipped());
        when(webhook.send(any())).thenReturn(ChannelSendResult.skipped());

        dispatcher.dispatch(USER_ID, EVENT_TRIGGERED, device(), record(), Map.of());

        verifyNoInteractions(deliveryService);
    }

    @Test
    void dispatch_should_keep_webhook_when_new_capabilities_disabled() {
        properties.setEnabled(false);
        when(webhook.send(any())).thenReturn(ChannelSendResult.success("example.com"));

        dispatcher.dispatch(USER_ID, EVENT_TRIGGERED, device(), record(), Map.of());

        verify(inapp, never()).send(any());
        verify(webhook).send(any());
    }

    @Test
    void dispatch_should_noop_on_missing_record() {
        dispatcher.dispatch(USER_ID, EVENT_TRIGGERED, device(), null, Map.of());

        verify(inapp, never()).send(any());
        verify(webhook, never()).send(any());
        verifyNoInteractions(deliveryService);
    }

    @Test
    void dispatch_should_deliver_configured_channel_once_per_matched_row() {
        NotificationChannel email = channel("EMAIL");
        dispatcher = new NotificationDispatcher(List.of(email, inapp, webhook), properties, deliveryService,
                configService, objectMapper, Runnable::run);
        when(configService.listActiveMatching(USER_ID, "EMAIL", EVENT_TRIGGERED))
                .thenReturn(List.of(row(21L), row(22L)));
        when(email.send(any())).thenReturn(ChannelSendResult.success("ops@example.com"));

        dispatcher.dispatch(USER_ID, EVENT_TRIGGERED, device(), record(), Map.of());

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(email, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationMessage::getChannelId)
                .containsExactly(21L, 22L);
        verify(deliveryService).record(any(), eq("EMAIL"), eq(21L), any());
        verify(deliveryService).record(any(), eq("EMAIL"), eq(22L), any());
    }

    @Test
    void dispatch_should_not_record_when_no_configured_row_matches() {
        NotificationChannel sms = channel("SMS");
        dispatcher = new NotificationDispatcher(List.of(sms), properties, deliveryService,
                configService, objectMapper, Runnable::run);
        when(configService.listActiveMatching(USER_ID, "SMS", EVENT_TRIGGERED)).thenReturn(List.of());

        dispatcher.dispatch(USER_ID, EVENT_TRIGGERED, device(), record(), Map.of());

        verify(sms, never()).send(any());
        verifyNoInteractions(deliveryService);
    }

    private NotificationChannelConfig row(Long id) {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setId(id);
        config.setUserId(USER_ID);
        config.setChannelType("EMAIL");
        return config;
    }

    private NotificationChannel channel(String type) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.channelType()).thenReturn(type);
        return channel;
    }

    private AlertRecord record() {
        AlertRecord record = new AlertRecord();
        record.setId(5L);
        record.setUserId(USER_ID);
        record.setTitle("温度过高");
        record.setSeverity("CRITICAL");
        record.setIdentifier("temperature");
        record.setTriggerValue("95");
        record.setTriggerCount(2);
        return record;
    }

    private Device device() {
        Device device = new Device();
        device.setId(101L);
        device.setOwnerId(USER_ID);
        device.setDeviceKey("dev-1");
        device.setDeviceName("网关一号");
        return device;
    }
}