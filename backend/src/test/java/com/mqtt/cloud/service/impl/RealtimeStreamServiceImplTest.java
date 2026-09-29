package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.entity.Device;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeStreamServiceImplTest {

    private final RealtimeStreamServiceImpl service = new RealtimeStreamServiceImpl();

    private Device device(Long id, Long ownerId) {
        Device d = new Device();
        d.setId(id);
        d.setOwnerId(ownerId);
        d.setDeviceKey("sensor-" + id);
        return d;
    }

    @Test
    void publish_should_only_reach_subscribers_with_permission() {
        SseEmitter ownerEmitter = service.subscribe(1L, false);
        SseEmitter otherEmitter = service.subscribe(2L, false);
        SseEmitter adminEmitter = service.subscribe(99L, true);

        service.publish(device(7L, 1L), "device/sensor-7/data", "{\"t\":1}");

        List<Long> recipients = service.debugRecipientUserIds();
        assertThat(recipients).containsExactlyInAnyOrder(1L, 99L);
        assertThat(service.debugSubscriberCount()).isEqualTo(3);
        assertThat(ownerEmitter).isNotNull();
        assertThat(otherEmitter).isNotNull();
        assertThat(adminEmitter).isNotNull();
    }

    @Test
    void subscribe_should_replace_previous_emitter_for_same_user() {
        service.subscribe(1L, false);
        service.subscribe(1L, false);

        assertThat(service.debugSubscriberCount()).isEqualTo(1);
    }
}