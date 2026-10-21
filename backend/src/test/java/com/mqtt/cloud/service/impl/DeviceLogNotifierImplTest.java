package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.RealtimeStreamService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 设备日志实时通知单测（T-41）。
 */
class DeviceLogNotifierImplTest {

    private final RealtimeStreamService realtimeStreamService = mock(RealtimeStreamService.class);
    private final DeviceLogNotifierImpl notifier = new DeviceLogNotifierImpl(realtimeStreamService);

    @Test
    void notify_should_publish_device_log_event() {
        Device device = device();

        notifier.notify(device);

        verify(realtimeStreamService).publishEvent(eq(10L), eq("device-log"), anyMap());
    }

    @Test
    void payload_should_carry_device_identity() {
        Device device = device();

        notifier.notify(device);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(realtimeStreamService).publishEvent(eq(10L), eq("device-log"), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("deviceId", 101L)
                .containsEntry("deviceKey", "dev-1")
                .containsKey("occurredAt");
    }

    @Test
    void null_device_should_be_ignored() {
        notifier.notify(null);
        verify(realtimeStreamService, never()).publishEvent(any(), any(), anyMap());
    }

    @Test
    void sse_failure_should_be_swallowed() {
        doThrow(new RuntimeException("sse down"))
                .when(realtimeStreamService).publishEvent(any(), any(), anyMap());

        notifier.notify(device());
    }

    private Device device() {
        Device device = new Device();
        device.setId(101L);
        device.setDeviceKey("dev-1");
        device.setOwnerId(10L);
        return device;
    }
}
