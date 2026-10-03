package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.constant.OtaStatusValue;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.OtaUpgradeRecordMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.OtaProgressPayload;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OTA 进度回传服务单测（T-22 实施计划 P6）。
 * <p>
 * 覆盖：非 {@code ota} 事件跳过、状态前向迁移、{@code failed} 旁支直达、进度单调不减、
 * 版本不一致忽略、无归属记录忽略、终态记录不覆盖、报文非法丢弃、逐条异常隔离、
 * 终态触发任务计数重算；另附 {@link OtaProgressPayload#parse} 报文解析的边界用例。
 */
class OtaProgressServiceImplTest {

    private static final Long DEVICE_ID_1 = 101L;
    private static final Long DEVICE_ID_2 = 102L;
    private static final Long TASK_ID = 5L;
    private static final Long RECORD_ID = 7L;
    private static final String VERSION = "1.0.0";

    private OtaUpgradeRecordMapper recordMapper;
    private OtaUpgradeTaskMapper taskMapper;
    private ObjectMapper objectMapper;
    private OtaProgressServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OtaUpgradeRecord.class);
        recordMapper = mock(OtaUpgradeRecordMapper.class);
        taskMapper = mock(OtaUpgradeTaskMapper.class);
        objectMapper = new ObjectMapper();
        service = new OtaProgressServiceImpl(recordMapper, taskMapper, objectMapper);
    }

    // ---------- 事件分发 ----------

    @Test
    void handle_should_ignore_null_or_empty_events() {
        service.handle(null);
        service.handle(List.of());

        verifyNoInteractions(recordMapper, taskMapper);
    }

    @Test
    void handle_should_skip_non_ota_message_type() {
        Device device = device(DEVICE_ID_1);
        service.handle(List.of(event(device, "telemetry", payload("downloading", 10))));

        verifyNoInteractions(recordMapper, taskMapper);
    }

    @Test
    void handle_should_skip_event_with_missing_parts() {
        Device device = device(DEVICE_ID_1);
        service.handle(java.util.Arrays.asList(
                null,
                new ResolvedEvent(null, ingest(device, "ota", payload("downloading", 10))),
                new ResolvedEvent(device, null)));

        verifyNoInteractions(recordMapper, taskMapper);
    }

    @Test
    void handle_should_isolate_single_event_failure() {
        Device device1 = device(DEVICE_ID_1);
        Device device2 = device(DEVICE_ID_2);
        // 第一条归位查询抛异常，第二条正常推进
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1)).thenThrow(new IllegalStateException("db down"));
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_2))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_2, OtaStatusValue.RECORD_DISPATCHED, 0, VERSION));

        service.handle(List.of(
                event(device1, "ota", payload("downloading", 30)),
                event(device2, "ota", payload("downloading", 40))));

        // 异常被隔离，第二条仍落库
        Map<String, Object> params = captureUpdateParams();
        assertThat(params.values()).contains(OtaStatusValue.RECORD_DOWNLOADING, 40);
    }

    // ---------- 状态机与进度 ----------

    @Test
    void handle_should_advance_status_and_progress() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_DISPATCHED, 0, VERSION));

        service.handle(List.of(event(device, "ota", payload("downloading", 30))));

        Map<String, Object> params = captureUpdateParams();
        assertThat(params.values()).contains(OtaStatusValue.RECORD_DOWNLOADING, 30);
        // 非终态不触发任务计数重算
        verify(taskMapper, never()).refreshCounters(anyLong());
    }

    @Test
    void handle_should_reach_success_with_full_progress_and_refresh_counters() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_FLASHING, 90, VERSION));

        service.handle(List.of(event(device, "ota", payload("success", null))));

        Map<String, Object> params = captureUpdateParams();
        // success 恒为 100，即使设备漏报进度
        assertThat(params.values()).contains(OtaStatusValue.RECORD_SUCCESS, 100);
        verify(taskMapper).refreshCounters(TASK_ID);
    }

    @Test
    void handle_should_mark_failed_from_any_non_terminal_state_and_refresh_counters() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_DOWNLOADING, 20, VERSION));

        service.handle(List.of(event(device, "ota", payload("failed", null))));

        Map<String, Object> params = captureUpdateParams();
        assertThat(params.values()).contains(OtaStatusValue.RECORD_FAILED);
        verify(taskMapper).refreshCounters(TASK_ID);
    }

    @Test
    void handle_should_not_regress_progress() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_DOWNLOADING, 80, VERSION));

        service.handle(List.of(event(device, "ota", payload("downloading", 10))));

        Map<String, Object> params = captureUpdateParams();
        // 状态与进度均保持既有值
        assertThat(params.values()).contains(OtaStatusValue.RECORD_DOWNLOADING, 80);
    }

    @Test
    void handle_should_keep_current_status_when_device_reports_stale_state() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_FLASHING, 95, VERSION));

        service.handle(List.of(event(device, "ota", payload("downloading", 50))));

        Map<String, Object> params = captureUpdateParams();
        assertThat(params.values()).contains(OtaStatusValue.RECORD_FLASHING, 95);
    }

    @Test
    void handle_should_keep_progress_when_device_omits_it() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_DISPATCHED, 15, VERSION));

        service.handle(List.of(event(device, "ota", payload("downloading", null))));

        Map<String, Object> params = captureUpdateParams();
        assertThat(params.values()).contains(OtaStatusValue.RECORD_DOWNLOADING, 15);
    }

    // ---------- 忽略场景 ----------

    @Test
    void handle_should_ignore_version_mismatch() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_DISPATCHED, 0, "2.0.0"));

        service.handle(List.of(event(device, "ota", payload("downloading", 30))));

        verify(recordMapper, never()).update(any(), any());
    }

    @Test
    void handle_should_ignore_when_no_active_record() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1)).thenReturn(null);

        service.handle(List.of(event(device, "ota", payload("downloading", 30))));

        verify(recordMapper, never()).update(any(), any());
    }

    @Test
    void handle_should_ignore_terminal_record() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_SUCCESS, 100, VERSION));

        service.handle(List.of(event(device, "ota", payload("downloading", 30))));

        verify(recordMapper, never()).update(any(), any());
        verify(taskMapper, never()).refreshCounters(anyLong());
    }

    @Test
    void handle_should_drop_invalid_payload() {
        Device device = device(DEVICE_ID_1);

        service.handle(List.of(event(device, "ota", "not-a-json")));

        verify(recordMapper, never()).selectLatestActiveByDevice(anyLong());
        verify(recordMapper, never()).update(any(), any());
    }

    @Test
    void handle_should_write_message_when_present() {
        Device device = device(DEVICE_ID_1);
        when(recordMapper.selectLatestActiveByDevice(DEVICE_ID_1))
                .thenReturn(record(RECORD_ID, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_DISPATCHED, 0, VERSION));

        service.handle(List.of(event(device, "ota",
                "{\"version\":\"1.0.0\",\"status\":\"failed\",\"message\":\"校验失败\"}")));

        assertThat(captureUpdateParams().values()).contains(OtaStatusValue.RECORD_FAILED, "校验失败");
    }

    // ---------- 报文解析 ----------

    @Test
    void parse_should_read_valid_downloading_payload() {
        OtaProgressPayload payload = OtaProgressPayload.parse(objectMapper,
                "{\"version\":\"1.0.0\",\"status\":\"downloading\",\"progress\":30}");

        assertThat(payload).isNotNull();
        assertThat(payload.version()).isEqualTo(VERSION);
        assertThat(payload.status()).isEqualTo(OtaStatusValue.DEVICE_DOWNLOADING);
        assertThat(payload.progress()).isEqualTo(30);
    }

    @Test
    void parse_should_normalize_upper_case_status() {
        OtaProgressPayload payload = OtaProgressPayload.parse(objectMapper,
                "{\"version\":\"1.0.0\",\"status\":\"FLASHING\"}");

        assertThat(payload).isNotNull();
        assertThat(payload.status()).isEqualTo(OtaStatusValue.DEVICE_FLASHING);
        assertThat(payload.progress()).isNull();
    }

    @Test
    void parse_should_force_full_progress_on_success() {
        OtaProgressPayload payload = OtaProgressPayload.parse(objectMapper,
                "{\"version\":\"1.0.0\",\"status\":\"success\",\"progress\":10}");

        assertThat(payload).isNotNull();
        assertThat(payload.progress()).isEqualTo(100);
    }

    @Test
    void parse_should_reject_missing_required_fields() {
        assertThat(OtaProgressPayload.parse(objectMapper, "{\"status\":\"downloading\"}")).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper, "{\"version\":\"1.0.0\"}")).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper, "{\"version\":\" \",\"status\":\"downloading\"}")).isNull();
    }

    @Test
    void parse_should_reject_unknown_status_and_illegal_progress() {
        assertThat(OtaProgressPayload.parse(objectMapper,
                "{\"version\":\"1.0.0\",\"status\":\"rebooting\"}")).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper,
                "{\"version\":\"1.0.0\",\"status\":\"downloading\",\"progress\":101}")).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper,
                "{\"version\":\"1.0.0\",\"status\":\"downloading\",\"progress\":\"30\"}")).isNull();
    }

    @Test
    void parse_should_reject_non_object_and_blank_payloads() {
        assertThat(OtaProgressPayload.parse(objectMapper, null)).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper, "  ")).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper, "[1,2,3]")).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper, "\"text\"")).isNull();
        assertThat(OtaProgressPayload.parse(objectMapper, "{oops")).isNull();
    }

    @Test
    void parse_should_truncate_overlong_message() {
        String longMessage = "y".repeat(400);
        OtaProgressPayload payload = OtaProgressPayload.parse(objectMapper,
                "{\"version\":\"1.0.0\",\"status\":\"failed\",\"message\":\"" + longMessage + "\"}");

        assertThat(payload).isNotNull();
        assertThat(payload.message()).hasSize(255);
    }

    // ---------- 辅助 ----------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> captureUpdateParams() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<OtaUpgradeRecord> wrapper = captor.getValue();
        // set 值惰性求值，须先渲染 SQL 才能填充参数表
        wrapper.getSqlSet();
        return wrapper.getParamNameValuePairs();
    }

    private String payload(String status, Integer progress) {
        StringBuilder sb = new StringBuilder("{\"version\":\"").append(VERSION)
                .append("\",\"status\":\"").append(status).append("\"");
        if (progress != null) {
            sb.append(",\"progress\":").append(progress);
        }
        return sb.append("}").toString();
    }

    private ResolvedEvent event(Device device, String messageType, String payload) {
        return new ResolvedEvent(device, ingest(device, messageType, payload));
    }

    private IngestRecord ingest(Device device, String messageType, String payload) {
        String key = device == null ? "key-unknown" : device.getDeviceKey();
        return new IngestRecord(key, "device/" + key + "/ota", messageType, payload, 0, LocalDateTime.now());
    }

    private Device device(Long id) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey("key-" + id);
        device.setDeviceName("设备" + id);
        return device;
    }

    private OtaUpgradeRecord record(Long id, Long taskId, Long deviceId, String status, Integer progress, String version) {
        OtaUpgradeRecord record = new OtaUpgradeRecord();
        record.setId(id);
        record.setTaskId(taskId);
        record.setDeviceId(deviceId);
        record.setStatus(status);
        record.setProgress(progress);
        record.setVersion(version);
        return record;
    }
}
