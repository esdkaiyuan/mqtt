package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.ShadowProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.service.DeviceCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备上线补发分发单测（T-16 实施计划 §9.2）。
 * <p>
 * 覆盖：空批 / 关闭开关 / 非上线消息的短路、{@code countQueued} 轻量短路、
 * 跨事件同设备去重、单设备异常隔离不冒泡。
 */
class ShadowDeliveryServiceImplTest {

    private DeviceCommandService deviceCommandService;
    private ShadowProperties properties;
    private ShadowDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        deviceCommandService = mock(DeviceCommandService.class);
        properties = new ShadowProperties();
        service = new ShadowDeliveryServiceImpl(deviceCommandService, properties);
    }

    private ResolvedEvent event(long deviceId, String messageType) {
        Device device = new Device();
        device.setId(deviceId);
        IngestRecord record = new IngestRecord("dev-" + deviceId, "topic/" + deviceId,
                messageType, "{}", 0, LocalDateTime.now());
        return new ResolvedEvent(device, record);
    }

    @Test
    void onIngest_should_skip_when_events_empty() {
        service.onIngest(null);
        service.onIngest(List.of());

        verifyNoInteractions(deviceCommandService);
    }

    @Test
    void onIngest_should_skip_when_resend_disabled() {
        properties.setResendEnabled(false);

        service.onIngest(List.of(event(1L, "data")));

        verifyNoInteractions(deviceCommandService);
    }

    @Test
    void onIngest_should_skip_when_no_online_message_type() {
        service.onIngest(List.of(event(1L, "event"), event(2L, "property")));

        verifyNoInteractions(deviceCommandService);
    }

    @Test
    void onIngest_should_flush_device_with_queued_commands() {
        when(deviceCommandService.countQueued(1L)).thenReturn(2);
        when(deviceCommandService.flushQueued(eq(1L), anyInt())).thenReturn(2);

        service.onIngest(List.of(event(1L, "data")));

        verify(deviceCommandService).flushQueued(eq(1L), anyInt());
    }

    @Test
    void onIngest_should_skip_device_when_nothing_queued() {
        when(deviceCommandService.countQueued(1L)).thenReturn(0);

        service.onIngest(List.of(event(1L, "heartbeat")));

        verify(deviceCommandService, never()).flushQueued(eq(1L), anyInt());
    }

    @Test
    void onIngest_should_deduplicate_same_device_across_events() {
        when(deviceCommandService.countQueued(1L)).thenReturn(1);
        when(deviceCommandService.flushQueued(eq(1L), anyInt())).thenReturn(1);

        service.onIngest(List.of(event(1L, "data"), event(1L, "heartbeat")));

        verify(deviceCommandService, times(1)).countQueued(1L);
        verify(deviceCommandService, times(1)).flushQueued(eq(1L), anyInt());
    }

    @Test
    void onIngest_should_isolate_single_device_failure() {
        when(deviceCommandService.countQueued(1L)).thenThrow(new IllegalStateException("boom"));
        when(deviceCommandService.countQueued(2L)).thenReturn(1);
        when(deviceCommandService.flushQueued(eq(2L), anyInt())).thenReturn(1);

        assertThatCode(() -> service.onIngest(List.of(event(1L, "data"), event(2L, "data"))))
                .doesNotThrowAnyException();

        verify(deviceCommandService).flushQueued(eq(2L), anyInt());
    }
}
