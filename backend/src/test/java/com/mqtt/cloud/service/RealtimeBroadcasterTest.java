package com.mqtt.cloud.service;

import tools.jackson.databind.ObjectMapper;
import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.entity.Device;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeBroadcasterTest {

    private static final String CHANNEL = "mqtt:realtime:device-data";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RealtimeStreamService streamService = mock(RealtimeStreamService.class);

    @SuppressWarnings("unchecked")
    private RealtimeBroadcaster broadcaster(boolean broadcastEnabled, RedisTemplate<String, String> template) {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setBroadcastEnabled(broadcastEnabled);
        properties.setBroadcastChannel(CHANNEL);
        return new RealtimeBroadcaster(properties, streamService, template, objectMapper, registry);
    }

    private Device device() {
        Device device = new Device();
        device.setId(7L);
        device.setDeviceKey("sensor-7");
        device.setOwnerId(1L);
        return device;
    }

    private double counter(String name) {
        return registry.get(name).counter().count();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_publish_local_only_when_broadcast_disabled() {
        RedisTemplate<String, String> template = mock(RedisTemplate.class);

        broadcaster(false, template).broadcast(device(), "device/sensor-7/data", "{\"t\":1}");

        verify(streamService).publishLocal(7L, "sensor-7", 1L, "device/sensor-7/data", "{\"t\":1}");
        verify(template, never()).convertAndSend(anyString(), any());
        assertThat(counter("realtime.broadcast.published")).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_publish_json_to_channel_when_enabled() {
        RedisTemplate<String, String> template = mock(RedisTemplate.class);

        broadcaster(true, template).broadcast(device(), "device/sensor-7/data", "{\"t\":1}");

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(template).convertAndSend(eq(CHANNEL), json.capture());
        assertThat(json.getValue())
                .contains("\"deviceId\":7")
                .contains("\"deviceKey\":\"sensor-7\"")
                .contains("\"ownerId\":1")
                .contains("\"topic\":\"device/sensor-7/data\"")
                .contains("\"payload\":\"{\\\"t\\\":1}\"")
                .contains("\"ts\":");
        // 广播成功后不再本地直推，避免同一副本重复下发
        verify(streamService, never()).publishLocal(any(), anyString(), any(), anyString(), anyString());
        assertThat(counter("realtime.broadcast.published")).isEqualTo(1.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fall_back_to_local_when_publish_fails() {
        RedisTemplate<String, String> template = mock(RedisTemplate.class);
        when(template.convertAndSend(anyString(), any())).thenThrow(new RuntimeException("redis down"));

        broadcaster(true, template).broadcast(device(), "device/sensor-7/data", "{\"t\":1}");

        verify(streamService).publishLocal(7L, "sensor-7", 1L, "device/sensor-7/data", "{\"t\":1}");
        assertThat(counter("realtime.broadcast.publish.failures")).isEqualTo(1.0);
    }
}