package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.entity.DevicePointRun;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.mapper.DevicePointRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点位采集日志三态落库单测（T-31 P4）。
 */
class PointRunRecorderTest {

    private DevicePointRunMapper mapper;
    private PointRunRecorder recorder;

    @BeforeEach
    void setUp() {
        mapper = mock(DevicePointRunMapper.class);
        recorder = new PointRunRecorder(mapper);
    }

    @Test
    void record_should_map_success_and_failure_rows() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 21, 12, 0);
        recorder.record(List.of(
                new PointValue(1L, 9L, "temp", true, "26", "26.0", null, at),
                new PointValue(2L, 9L, "hum", false, null, null, "read timeout", at)));

        List<DevicePointRun> rows = captureRows(1);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getStatus()).isEqualTo(DevicePointRun.STATUS_OK);
        assertThat(rows.get(0).getValue()).isEqualTo("26.0");
        assertThat(rows.get(0).getCollectedAt()).isEqualTo(at);
        assertThat(rows.get(1).getStatus()).isEqualTo(DevicePointRun.STATUS_FAILED);
        assertThat(rows.get(1).getErrorMessage()).isEqualTo("read timeout");
    }

    @Test
    void skipped_should_write_skipped_rows_with_reason() {
        recorder.skipped(List.of(point(1L), point(2L)), "设备已停用");

        List<DevicePointRun> rows = captureRows(1);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(row -> DevicePointRun.STATUS_SKIPPED.equals(row.getStatus()));
        assertThat(rows).allMatch(row -> row.getErrorMessage().contains("设备已停用"));
        assertThat(rows).allMatch(row -> row.getCollectedAt() != null);
    }

    @Test
    void failed_should_write_failed_rows() {
        recorder.failed(List.of(point(3L)), "连接失败");

        List<DevicePointRun> rows = captureRows(1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(DevicePointRun.STATUS_FAILED);
    }

    @Test
    void long_value_and_error_should_be_truncated_to_column_limits() {
        String longText = "x".repeat(600);
        recorder.record(List.of(new PointValue(1L, 9L, "p", true, longText, longText, null,
                LocalDateTime.now())));
        recorder.failed(List.of(point(1L)), longText);

        List<DevicePointRun> rows = captureRows(2);
        assertThat(rows.get(0).getValue()).hasSize(255);
        assertThat(rows.get(0).getRawValue()).hasSize(255);
        assertThat(rows.get(1).getErrorMessage()).hasSize(500);
    }

    @Test
    void more_than_batch_size_should_split_inserts() {
        List<PointValue> values = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            values.add(new PointValue((long) i, 9L, "p" + i, true, "1", "1", null,
                    LocalDateTime.now()));
        }

        recorder.record(values);

        verify(mapper, times(2)).insertBatch(anyList());
    }

    @SuppressWarnings("unchecked")
    private List<DevicePointRun> captureRows(int expectedCalls) {
        ArgumentCaptor<List<DevicePointRun>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper, times(expectedCalls)).insertBatch(captor.capture());
        List<DevicePointRun> rows = new ArrayList<>();
        captor.getAllValues().forEach(rows::addAll);
        return rows;
    }

    private DevicePointTable point(Long id) {
        DevicePointTable point = new DevicePointTable();
        point.setId(id);
        point.setDeviceId(9L);
        point.setPointKey("p" + id);
        return point;
    }
}
