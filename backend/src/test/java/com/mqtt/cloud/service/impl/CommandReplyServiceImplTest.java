package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.DeviceCommandRecordMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 命令回执处理单测（T-15 实施计划 P8）。
 * <p>
 * 回执只按 {@code id} 关联命令记录，条件更新（仅 {@code PENDING/SENT} 可迁移）决定结果：
 * 受影响行数为 0 表示未知回执或已终态 —— 终态一经写入不可被覆盖。
 */
class CommandReplyServiceImplTest {

    private static final String METRIC = "command_reply_total";
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 2, 12, 0, 0);

    private DeviceCommandRecordMapper commandMapper;
    private SimpleMeterRegistry registry;
    private CommandReplyServiceImpl service;

    @BeforeEach
    void setUp() {
        commandMapper = mock(DeviceCommandRecordMapper.class);
        registry = new SimpleMeterRegistry();
        service = new CommandReplyServiceImpl(commandMapper, new ObjectMapper(), registry);
    }

    @Test
    void handle_should_mark_acked_on_code_200() {
        when(commandMapper.markAcked(eq("cmd-1"), any(), any())).thenReturn(1);

        service.handle(List.of(reply("{\"id\":\"cmd-1\",\"code\":200,\"data\":{\"ok\":true}}")));

        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(commandMapper).markAcked(eq("cmd-1"), result.capture(), any());
        assertThat(result.getValue()).isEqualTo("{\"ok\":true}");
        verify(commandMapper, never()).markFailed(anyString(), anyString(), any());
        assertThat(count("acked")).isEqualTo(1);
    }

    @Test
    void handle_should_mark_failed_on_non_200_code() {
        when(commandMapper.markFailed(eq("cmd-2"), anyString(), any())).thenReturn(1);

        service.handle(List.of(reply("{\"id\":\"cmd-2\",\"code\":500,\"data\":\"boom\"}")));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(commandMapper).markFailed(eq("cmd-2"), message.capture(), any());
        assertThat(message.getValue()).contains("code=500").contains("data=boom");
        verify(commandMapper, never()).markAcked(anyString(), any(), any());
        assertThat(count("failed")).isEqualTo(1);
    }

    @Test
    void handle_should_ignore_unknown_or_terminal_id() {
        when(commandMapper.markAcked(anyString(), any(), any())).thenReturn(0);

        service.handle(List.of(reply("{\"id\":\"cmd-3\",\"code\":200}")));

        assertThat(count("ignored")).isEqualTo(1);
        assertThat(count("acked")).isZero();
    }

    @Test
    void handle_should_count_invalid_when_id_missing_or_payload_bad() {
        service.handle(List.of(reply("{\"code\":200}")));
        service.handle(List.of(reply("not-json")));
        service.handle(List.of(reply("[1,2]")));

        assertThat(count("invalid")).isEqualTo(3);
        verifyNoInteractions(commandMapper);
    }

    @Test
    void handle_should_skip_non_reply_message_type() {
        ResolvedEvent data = new ResolvedEvent(device(),
                new IngestRecord("dev-1", "mqtt/data", "data", "{\"id\":\"cmd-4\",\"code\":200}", 0, RECEIVED_AT));

        service.handle(List.of(data));

        verifyNoInteractions(commandMapper);
        assertThat(count("acked")).isZero();
    }

    @Test
    void handle_should_noop_on_empty_or_null() {
        service.handle(List.of());
        service.handle(null);

        verifyNoInteractions(commandMapper);
    }

    @Test
    void handle_should_isolate_failure_per_record() {
        when(commandMapper.markAcked(eq("cmd-5"), any(), any())).thenThrow(new RuntimeException("db down"));
        when(commandMapper.markAcked(eq("cmd-6"), any(), any())).thenReturn(1);

        service.handle(List.of(
                reply("{\"id\":\"cmd-5\",\"code\":200}"),
                reply("{\"id\":\"cmd-6\",\"code\":200}")));

        assertThat(count("invalid")).isEqualTo(1);
        assertThat(count("acked")).isEqualTo(1);
        verify(commandMapper).markAcked(eq("cmd-6"), any(), any());
    }

    @Test
    void handle_should_tolerate_method_not_matching() {
        when(commandMapper.markAcked(eq("cmd-7"), any(), any())).thenReturn(1);

        service.handle(List.of(reply("{\"id\":\"cmd-7\",\"method\":\"thing.service.setMode\",\"code\":200}")));

        assertThat(count("acked")).isEqualTo(1);
    }

    // ---------- 辅助 ----------

    private ResolvedEvent reply(String payload) {
        return new ResolvedEvent(device(),
                new IngestRecord("dev-1", "device/dev-1/reply", "reply", payload, 1, RECEIVED_AT));
    }

    private Device device() {
        Device device = new Device();
        device.setId(100L);
        device.setDeviceKey("dev-1");
        return device;
    }

    private double count(String result) {
        Counter counter = registry.find(METRIC).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }
}