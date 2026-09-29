package com.mqtt.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RealtimeChannelSubscriberTest {

    private static final String CHANNEL = "mqtt:realtime:device-data";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RealtimeStreamService streamService = mock(RealtimeStreamService.class);
    private final RealtimeChannelSubscriber subscriber =
            new RealtimeChannelSubscriber(streamService, objectMapper, registry);

    private void deliver(String body) {
        Message message = new DefaultMessage(CHANNEL.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
        subscriber.onMessage(message, null);
    }

    private double counter(String name) {
        return registry.get(name).counter().count();
    }

    @Test
    void should_fanout_parsed_message_to_local_stream() {
        deliver("{\"deviceId\":7,\"deviceKey\":\"sensor-7\",\"ownerId\":1,"
                + "\"topic\":\"device/sensor-7/data\",\"payload\":\"{\\\"t\\\":1}\",\"ts\":1759000000000}");

        verify(streamService).publishLocal(7L, "sensor-7", 1L, "device/sensor-7/data", "{\"t\":1}");
        assertThat(counter("realtime.broadcast.received")).isEqualTo(1.0);
        assertThat(counter("realtime.broadcast.parse.failures")).isZero();
    }

    @Test
    void should_pass_null_owner_through_for_admin_only_visibility() {
        deliver("{\"deviceId\":7,\"deviceKey\":\"sensor-7\",\"ownerId\":null,"
                + "\"topic\":\"device/sensor-7/data\",\"payload\":\"{}\",\"ts\":1}");

        verify(streamService).publishLocal(7L, "sensor-7", null, "device/sensor-7/data", "{}");
    }

    @Test
    void should_ignore_malformed_json_without_throwing() {
        assertThatCode(() -> deliver("not-a-json")).doesNotThrowAnyException();

        verify(streamService, never()).publishLocal(anyLong(), anyString(), any(), anyString(), anyString());
        assertThat(counter("realtime.broadcast.received")).isEqualTo(1.0);
        assertThat(counter("realtime.broadcast.parse.failures")).isEqualTo(1.0);
    }
}