package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.event.DeviceStatusChangedEvent;
import com.mqtt.cloud.service.SceneEvaluationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SceneStatusTriggerListenerTest {

    @Mock
    private SceneEvaluationService sceneEvaluationService;

    @InjectMocks
    private SceneStatusTriggerListener listener;

    @Test
    void should_delegate_status_event_to_evaluation() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 21, 10, 0);

        listener.onDeviceStatusChanged(
                new DeviceStatusChangedEvent(1L, DeviceStatusValue.ONLINE, occurredAt));

        verify(sceneEvaluationService).onStatus(1L, DeviceStatusValue.ONLINE, occurredAt);
    }

    @Test
    void should_swallow_evaluation_failure() {
        doThrow(new RuntimeException("boom"))
                .when(sceneEvaluationService).onStatus(any(), anyString(), any());

        assertThatCode(() -> listener.onDeviceStatusChanged(
                new DeviceStatusChangedEvent(1L, DeviceStatusValue.OFFLINE, LocalDateTime.now())))
                .doesNotThrowAnyException();

        verify(sceneEvaluationService).onStatus(any(), anyString(), any());
    }
}
