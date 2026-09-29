package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.entity.Device;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeStreamServiceImplTest {

    private final RealtimeStreamServiceImpl service = new RealtimeStreamServiceImpl();

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

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
    void publishLocal_should_reach_only_admin_when_device_has_no_owner() {
        service.subscribe(1L, false);
        service.subscribe(99L, true);

        service.publishLocal(7L, "sensor-7", null, "device/sensor-7/data", "{\"t\":1}");

        assertThat(service.debugRecipientUserIds()).containsExactly(99L);
    }

    @Test
    void subscribe_should_keep_multiple_connections_for_same_user() {
        service.subscribe(1L, false);
        service.subscribe(1L, false);
        service.subscribe(99L, true);

        assertThat(service.debugSubscriberCount()).isEqualTo(3);

        service.publish(device(7L, 1L), "device/sensor-7/data", "{\"t\":1}");

        // 同一用户的两个连接都应收到，不会被新连接踢掉
        assertThat(service.debugRecipientUserIds()).containsExactlyInAnyOrder(1L, 1L, 99L);
    }

    @Test
    void heartbeat_should_do_nothing_when_no_subscriber() {
        assertThat(service.debugHeartbeatOnce()).isZero();
    }

    @Test
    void heartbeat_should_reach_every_connection() {
        service.subscribe(1L, false);
        service.subscribe(1L, false);
        service.subscribe(99L, true);

        assertThat(service.debugHeartbeatOnce()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void gauge_should_track_subscriber_count() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        RealtimeStreamServiceImpl monitored = new RealtimeStreamServiceImpl(new RealtimeProperties(), provider);
        try {
            monitored.subscribe(1L, false);
            monitored.subscribe(1L, false);
            monitored.subscribe(99L, true);

            assertThat(registry.get("realtime.sse.subscribers").gauge().value()).isEqualTo(3.0);
        } finally {
            monitored.shutdown();
        }
    }
}