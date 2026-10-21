package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.constant.ShadowChangeSide;
import com.mqtt.cloud.event.DeviceShadowChangedEvent;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.SceneEvaluationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SceneShadowTriggerListenerTest {

    @Mock
    private SceneEvaluationService sceneEvaluationService;

    @InjectMocks
    private SceneShadowTriggerListener listener;

    @Test
    void should_delegate_shadow_event_to_evaluation() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 21, 10, 0);
        Map<String, String> changes = Map.of("temperature", "31");

        listener.onShadowChanged(new DeviceShadowChangedEvent(1L, ShadowChangeSide.REPORTED,
                changes, 5L, "INGEST", occurredAt));

        verify(sceneEvaluationService).onShadow(1L, ShadowChangeSide.REPORTED, changes,
                "INGEST", occurredAt);
    }

    @Test
    void should_skip_internal_desired_source() {
        listener.onShadowChanged(new DeviceShadowChangedEvent(1L, ShadowChangeSide.DESIRED,
                Map.of("power", "true"), 2L, DeviceCommandService.SOURCE_SCENE,
                LocalDateTime.now()));

        verifyNoInteractions(sceneEvaluationService);
    }

    @Test
    void should_swallow_evaluation_failure() {
        doThrow(new RuntimeException("boom"))
                .when(sceneEvaluationService).onShadow(any(), anyString(), any(), any(), any());

        assertThatCode(() -> listener.onShadowChanged(new DeviceShadowChangedEvent(1L,
                ShadowChangeSide.DESIRED, Map.of("power", "true"), 2L, "CONSOLE",
                LocalDateTime.now())))
                .doesNotThrowAnyException();

        verify(sceneEvaluationService).onShadow(any(), anyString(), any(), any(), any());
    }
}
