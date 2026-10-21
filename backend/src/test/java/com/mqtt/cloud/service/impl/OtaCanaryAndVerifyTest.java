package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.OtaStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.OtaProperties;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.request.OtaTaskCreateRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.entity.OtaUpgradeBatch;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import com.mqtt.cloud.entity.OtaUpgradeTask;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.OtaFirmwareMapper;
import com.mqtt.cloud.mapper.OtaUpgradeBatchMapper;
import com.mqtt.cloud.mapper.OtaUpgradeRecordMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.OtaProgressPayload;
import com.mqtt.cloud.service.ProductService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-32 专项单测：OTA 灰度分批（G-11-1）、设备级互斥（G-11-4）、固件完整性校验（G-11-2）。
 * <p>
 * 与 T-22 的 {@link OtaUpgradeServiceImplTest} / {@link OtaProgressServiceImplTest} 分开：
 * 后两者守「改造后行为不变」，本类守「新增能力按预期工作」，两类失败排查时指向不同方向。
 * <p>
 * SQL 层面的口径（SKIPPED 排除出 dispatched、PAUSED/ABORTED 不被聚合覆盖、灰度推进的四项前置条件）
 * 由 {@code OtaUpgradeRecordMapperSqlTest} 守卫，本类只覆盖服务层编排。
 */
class OtaCanaryAndVerifyTest {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 3L;
    private static final Long FIRMWARE_ID = 1L;
    private static final Long TASK_ID = 5L;
    private static final Long BATCH_ID_1 = 11L;
    private static final Long BATCH_ID_2 = 12L;
    private static final String VERSION = "1.0.0";
    private static final String MD5 = "d41d8cd98f00b204e9800998ecf8427e";
    /** 与 MD5 不同的一串合法 md5，用于构造「设备回传了错的摘要」。 */
    private static final String MD5_WRONG = "a1b2c3d4e5f60718293a4b5c6d7e8f90";

    private OtaFirmwareMapper firmwareMapper;
    private OtaUpgradeTaskMapper taskMapper;
    private OtaUpgradeRecordMapper recordMapper;
    private OtaUpgradeBatchMapper batchMapper;
    private DeviceMapper deviceMapper;
    private DeviceBatchService deviceBatchService;
    private DeviceCommandService deviceCommandService;
    private ProductService productService;
    private OtaProperties properties;
    private OtaUpgradeServiceImpl service;
    private OtaProgressServiceImpl progressService;

    private final List<OtaUpgradeRecord> insertedRecords = new ArrayList<>();
    private final List<OtaUpgradeBatch> insertedBatches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        initTable(OtaUpgradeRecord.class);
        initTable(OtaUpgradeTask.class);
        initTable(OtaUpgradeBatch.class);
        firmwareMapper = mock(OtaFirmwareMapper.class);
        taskMapper = mock(OtaUpgradeTaskMapper.class);
        recordMapper = mock(OtaUpgradeRecordMapper.class);
        batchMapper = mock(OtaUpgradeBatchMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        deviceBatchService = mock(DeviceBatchService.class);
        deviceCommandService = mock(DeviceCommandService.class);
        productService = mock(ProductService.class);
        properties = new OtaProperties();
        service = new OtaUpgradeServiceImpl(firmwareMapper, taskMapper, recordMapper, batchMapper, deviceMapper,
                deviceBatchService, deviceCommandService, productService, properties,
                new tools.jackson.databind.ObjectMapper());
        progressService = new OtaProgressServiceImpl(recordMapper, taskMapper, batchMapper, firmwareMapper,
                properties, new tools.jackson.databind.ObjectMapper());
        insertedRecords.clear();
        insertedBatches.clear();
    }

    private static void initTable(Class<?> entity) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
    }

    // ================= G-11-1 灰度分批 =================

    @Test
    void createTask_batched_creates_batches_and_dispatches_only_first_batch() {
        stubCreateBasics(List.of(device(1L), device(2L), device(3L), device(4L), device(5L)));

        service.createTask(USER_ID, request("灰度升级", OtaStatusValue.BATCH_MODE_BATCHED, 2, 0L));

        // 5 台按每批 2 台切成 3 批（2 / 2 / 1）
        assertThat(insertedBatches).hasSize(3);
        assertThat(insertedBatches).extracting(OtaUpgradeBatch::getBatchNo).containsExactly(1, 2, 3);
        assertThat(insertedBatches).extracting(OtaUpgradeBatch::getDeviceCount).containsExactly(2, 2, 1);
        assertThat(insertedBatches).allSatisfy(batch -> {
            assertThat(batch.getStatus()).isEqualTo(OtaStatusValue.BATCH_PENDING);
            assertThat(batch.getScheduledAt()).isNotNull();
        });
        // 记录按批次打标
        assertThat(insertedRecords).extracting(OtaUpgradeRecord::getBatchNo).containsExactly(1, 1, 2, 2, 3);
        // 只下发首批 2 台
        verify(deviceCommandService, times(2)).invoke(any());
    }

    @Test
    void createTask_batched_defaults_batch_size_and_interval() {
        stubCreateBasics(List.of(device(1L), device(2L), device(3L)));

        service.createTask(USER_ID, request("灰度升级", OtaStatusValue.BATCH_MODE_BATCHED, null, null));

        // 断言建任务时实际落库的 task 行：mock 的 selectById 恒返回初始值，detail 投影不可信
        OtaUpgradeTask persisted = captureInsertedTask();
        assertThat(persisted.getBatchSize()).isEqualTo(properties.getDefaultBatchSize());
        assertThat(persisted.getBatchIntervalMs()).isZero();
        assertThat(persisted.getBatchMode()).isEqualTo(OtaStatusValue.BATCH_MODE_BATCHED);
        assertThat(persisted.getBatchTotal()).isEqualTo(1);
        assertThat(insertedBatches).hasSize(1);
        assertThat(insertedBatches.get(0).getDeviceCount()).isEqualTo(3);
    }

    @Test
    void createTask_all_mode_creates_no_batch_rows() {
        stubCreateBasics(List.of(device(1L), device(2L)));

        var vo = service.createTask(USER_ID, request("全量升级", null, null, null));

        assertThat(insertedBatches).isEmpty();
        assertThat(vo.getBatchMode()).isEqualTo(OtaStatusValue.BATCH_MODE_ALL);
        assertThat(vo.getBatchTotal()).isEqualTo(1);
        assertThat(vo.getBatchSize()).isZero();
        // 记录仍带 batch_no=1，与 V19 存量行口径一致
        assertThat(insertedRecords).extracting(OtaUpgradeRecord::getBatchNo).containsOnly(1);
        verify(deviceCommandService, times(2)).invoke(any());
    }

    @Test
    void createTask_rejects_unknown_batch_mode() {
        OtaFirmware firmware = firmware();
        BatchTargetRequest target = target();
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware);
        when(deviceBatchService.resolveTarget(USER_ID, target)).thenReturn(List.of(device(1L)));

        OtaTaskCreateRequest req = request("乱填策略", "SLOWLY", 1, 0L);
        assertThatThrownBy(() -> service.createTask(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_BATCH_PARAM_INVALID.getCode());
    }

    @Test
    void createTask_rejects_negative_batch_interval() {
        OtaFirmware firmware = firmware();
        BatchTargetRequest target = target();
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware);
        when(deviceBatchService.resolveTarget(USER_ID, target)).thenReturn(List.of(device(1L)));

        OtaTaskCreateRequest req = request("负间隔", OtaStatusValue.BATCH_MODE_BATCHED, 2, -1L);
        assertThatThrownBy(() -> service.createTask(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_BATCH_PARAM_INVALID.getCode());
    }

    @Test
    void createTask_stamps_verify_status_not_reported_on_records() {
        stubCreateBasics(List.of(device(1L)));

        service.createTask(USER_ID, request("全量升级", null, null, null));

        // 存量语义是「未校验」，不得初始就写 PASSED 虚增可信度
        assertThat(insertedRecords).allSatisfy(record ->
                assertThat(record.getVerifyStatus()).isEqualTo(OtaStatusValue.VERIFY_NOT_REPORTED));
    }

    // ================= G-11-4 设备级互斥 =================

    @Test
    void createTask_skips_devices_conflicting_in_other_tasks() {
        stubCreateBasics(List.of(device(1L), device(2L), device(3L)));
        when(recordMapper.selectActiveDeviceIdsExcludingTask(any(), anyLong())).thenReturn(List.of(2L));

        var vo = service.createTask(USER_ID, request("全量升级", null, null, null));

        assertThat(insertedRecords).extracting(OtaUpgradeRecord::getDeviceId).containsExactly(1L, 3L);
        assertThat(vo.getConflictSkippedCount()).isEqualTo(1);
        verify(deviceCommandService, times(2)).invoke(any());
    }

    @Test
    void createTask_rejects_when_every_device_conflicts() {
        stubCreateBasics(List.of(device(1L), device(2L)));
        when(recordMapper.selectActiveDeviceIdsExcludingTask(any(), anyLong())).thenReturn(List.of(1L, 2L));

        OtaTaskCreateRequest req = request("全量升级", null, null, null);
        assertThatThrownBy(() -> service.createTask(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_DEVICE_CONFLICT.getCode());
    }

    @Test
    void createTask_rejects_any_conflict_when_policy_is_reject() {
        properties.setDeviceConflictPolicy("REJECT");
        stubCreateBasics(List.of(device(1L), device(2L), device(3L)));
        when(recordMapper.selectActiveDeviceIdsExcludingTask(any(), anyLong())).thenReturn(List.of(3L));

        OtaTaskCreateRequest req = request("全量升级", null, null, null);
        assertThatThrownBy(() -> service.createTask(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_DEVICE_CONFLICT.getCode());
        verify(deviceCommandService, never()).invoke(any());
    }

    @Test
    void dispatchTask_skips_devices_conflicting_in_other_tasks() {
        OtaUpgradeTask task = task();
        task.setBatchMode(OtaStatusValue.BATCH_MODE_BATCHED);
        task.setCurrentBatchNo(2);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware());
        when(recordMapper.selectList(any())).thenReturn(List.of(recordWithId(1L, 1L), recordWithId(2L, 2L)));
        when(recordMapper.selectActiveDeviceIdsExcludingTask(any(), anyLong())).thenReturn(List.of(2L));
        when(deviceCommandService.invoke(any())).thenAnswer(inv -> command("cmd-" + System.nanoTime()));

        int dispatched = service.dispatchTask(TASK_ID, List.of(1L, 2L));

        assertThat(dispatched).isEqualTo(1);
        verify(deviceCommandService, times(1)).invoke(any());
        // 冲突设备保持 PENDING 等补投，且写明原因（成功那台另有一条写 DISPATCHED 的 update）
        assertThat(captureRecordUpdateSqls()).anyMatch(sql -> sql.contains("message"));
    }

    @Test
    void dispatchTask_passes_exclude_id_equal_to_task_id() {
        OtaUpgradeTask task = task();
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware());
        when(recordMapper.selectList(any())).thenReturn(List.of(recordWithId(1L, 1L)));

        service.dispatchTask(TASK_ID, List.of(1L));

        // 本任务的既有记录不得被判成冲突，否则重投与灰度推进都会被自己挡住
        verify(recordMapper).selectActiveDeviceIdsExcludingTask(any(), eq(TASK_ID));
    }

    // ================= G-11-1 暂停 / 恢复 / 终止 / 推进 =================

    @Test
    void pause_sets_paused_status() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(productService.list()).thenReturn(List.of());
        when(firmwareMapper.selectBatchIds(any())).thenReturn(List.of(firmware()));

        service.pause(USER_ID, TASK_ID);

        // mock 的 selectById 恒返回初始 RUNNING，detail 投影不会跟着变；
        // 断言实际下发的 UPDATE 才是「暂停生效」的证据
        assertThat(captureTaskUpdateSql()).contains("status");
        assertThat(captureTaskUpdateParams().values()).contains(OtaStatusValue.TASK_PAUSED);
    }

    @Test
    void pause_rejects_non_running_task() {
        OtaUpgradeTask task = task();
        task.setStatus(OtaStatusValue.TASK_SUCCESS);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> service.pause(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_TASK_STATE_INVALID.getCode());
    }

    @Test
    void resume_restores_paused_task() {
        OtaUpgradeTask task = task();
        task.setStatus(OtaStatusValue.TASK_PAUSED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(productService.list()).thenReturn(List.of());
        when(firmwareMapper.selectBatchIds(any())).thenReturn(List.of(firmware()));

        service.resume(USER_ID, TASK_ID);

        assertThat(captureTaskUpdateSql()).contains("status");
        assertThat(captureTaskUpdateParams().values()).contains(OtaStatusValue.TASK_RUNNING);
    }

    @Test
    void resume_rejects_running_task() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());

        assertThatThrownBy(() -> service.resume(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_TASK_STATE_INVALID.getCode());
    }

    @Test
    void abort_marks_pending_records_and_batches_skipped() {
        OtaUpgradeTask task = task();
        task.setStatus(OtaStatusValue.TASK_PAUSED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(recordMapper.update(any(), any())).thenReturn(2);

        int skipped = service.abort(USER_ID, TASK_ID);

        assertThat(skipped).isEqualTo(2);
        assertThat(captureRecordUpdateSql()).contains("status").contains("message");
        assertThat(captureRecordUpdateParamsList().values()).contains(OtaStatusValue.RECORD_SKIPPED);
    }

    @Test
    void abort_rejects_finished_task() {
        OtaUpgradeTask task = task();
        task.setStatus(OtaStatusValue.TASK_SUCCESS);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> service.abort(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_TASK_STATE_INVALID.getCode());
    }

    @Test
    void remove_rejects_paused_task() {
        OtaUpgradeTask task = task();
        task.setStatus(OtaStatusValue.TASK_PAUSED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> service.remove(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_TASK_STATE_INVALID.getCode());
    }

    @Test
    void retry_rejects_aborted_task() {
        OtaUpgradeTask task = task();
        task.setStatus(OtaStatusValue.TASK_ABORTED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> service.retry(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_TASK_STATE_INVALID.getCode());
    }

    @Test
    void advance_dispatches_next_batch_and_bumps_batch_no() {
        OtaUpgradeTask task = task();
        task.setBatchMode(OtaStatusValue.BATCH_MODE_BATCHED);
        task.setCurrentBatchNo(1);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(batchMapper.selectOne(any())).thenReturn(batch(BATCH_ID_2, 2));
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware());
        when(recordMapper.selectList(any())).thenReturn(List.of(recordInBatch(1L, 2)));
        when(deviceCommandService.invoke(any())).thenAnswer(inv -> command("cmd"));

        int dispatched = service.advance(USER_ID, TASK_ID);

        assertThat(dispatched).isEqualTo(1);
        assertThat(captureTaskUpdateSql()).contains("current_batch_no");
        assertThat(captureTaskUpdateParams().values()).contains(2);
        assertThat(captureBatchUpdateSql()).contains("started_at");
        assertThat(captureBatchUpdateParams().values()).contains(OtaStatusValue.BATCH_RUNNING);
    }

    @Test
    void advance_rejects_all_mode_task() {
        OtaUpgradeTask task = task();
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> service.advance(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.OTA_TASK_STATE_INVALID.getCode());
    }

    @Test
    void advance_returns_zero_when_no_pending_batch_left() {
        OtaUpgradeTask task = task();
        task.setBatchMode(OtaStatusValue.BATCH_MODE_BATCHED);
        task.setCurrentBatchNo(3);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(batchMapper.selectOne(any())).thenReturn(null);

        assertThat(service.advance(USER_ID, TASK_ID)).isZero();
        verify(deviceCommandService, never()).invoke(any());
    }

    @Test
    void sweepBatchAdvance_is_noop_when_interval_not_configured() {
        assertThat(service.sweepBatchAdvance(0L, 5)).isZero();
        verify(batchMapper, never()).selectDueBatches(any(), anyLong(), anyInt());
    }

    @Test
    void sweepBatchAdvance_advances_due_batch_and_bumps_batch_no() {
        OtaUpgradeBatch batch = batch(BATCH_ID_2, 2);
        when(batchMapper.selectDueBatches(any(), anyLong(), anyInt())).thenReturn(List.of(batch));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware());
        when(recordMapper.selectList(any())).thenReturn(List.of(recordInBatch(1L, 2), recordInBatch(2L, 2)));
        when(deviceCommandService.invoke(any())).thenAnswer(inv -> command("cmd"));

        int dispatched = service.sweepBatchAdvance(600_000L, 5);

        assertThat(dispatched).isEqualTo(2);
        assertThat(captureTaskUpdateSql()).contains("current_batch_no");
    }

    // ================= G-11-2 固件完整性校验 =================

    @Test
    void progress_md5_match_passes_verification() {
        stubProgress(record(OtaStatusValue.RECORD_FLASHING), 50, MD5);

        progressService.handle(List.of(event(otaPayload("success", MD5))));

        Map<String, Object> params = captureRecordUpdateParams();
        assertThat(params.values())
                .contains(OtaStatusValue.RECORD_SUCCESS, OtaStatusValue.VERIFY_PASSED, MD5);
    }

    @Test
    void progress_md5_mismatch_fails_record() {
        stubProgress(record(OtaStatusValue.RECORD_FLASHING), 50, MD5);

        progressService.handle(List.of(event(otaPayload("success", MD5_WRONG))));

        Map<String, Object> params = captureRecordUpdateParams();
        assertThat(params.values())
                .contains(OtaStatusValue.RECORD_FAILED, OtaStatusValue.VERIFY_MISMATCHED, MD5_WRONG);
        // 进度不得停在 100%：校验失败意味着根本没成功
        assertThat(params.values()).doesNotContain(100);
        verify(taskMapper).refreshCounters(TASK_ID);
        verify(batchMapper).refreshBatchCounters(TASK_ID, 1);
    }

    @Test
    void progress_without_md5_is_allowed_by_default() {
        stubProgress(record(OtaStatusValue.RECORD_FLASHING), 50, MD5);

        progressService.handle(List.of(event(otaPayload("success", null))));

        Map<String, Object> params = captureRecordUpdateParams();
        assertThat(params.values())
                .contains(OtaStatusValue.RECORD_SUCCESS, OtaStatusValue.VERIFY_NOT_REPORTED);
    }

    @Test
    void progress_without_md5_fails_when_verify_required() {
        properties.setVerifyRequired(true);
        stubProgress(record(OtaStatusValue.RECORD_FLASHING), 50, MD5);

        progressService.handle(List.of(event(otaPayload("success", null))));

        Map<String, Object> params = captureRecordUpdateParams();
        assertThat(params.values())
                .contains(OtaStatusValue.RECORD_FAILED, OtaStatusValue.VERIFY_NOT_REPORTED);
    }

    @Test
    void progress_verifying_advances_to_verify_state() {
        stubProgress(record(OtaStatusValue.RECORD_DOWNLOADING), 30, MD5);

        progressService.handle(List.of(event(otaPayload("verifying", null))));

        Map<String, Object> params = captureRecordUpdateParams();
        assertThat(params.values())
                .contains(OtaStatusValue.RECORD_VERIFYING, OtaStatusValue.VERIFY_VERIFYING);
    }

    @Test
    void progress_downloading_does_not_touch_verify_columns() {
        stubProgress(record(OtaStatusValue.RECORD_DISPATCHED), 0, MD5);

        progressService.handle(List.of(event(otaPayload("downloading", null, 40))));

        Map<String, Object> params = captureRecordUpdateParams();
        assertThat(params.values()).contains(OtaStatusValue.RECORD_DOWNLOADING, 40);
        assertThat(params.values()).doesNotContain(OtaStatusValue.VERIFY_PASSED);
    }

    @Test
    void payload_normalizes_uppercase_md5() {
        OtaProgressPayload payload = OtaProgressPayload.parse(new tools.jackson.databind.ObjectMapper(),
                "{\"version\":\"1.0.0\",\"status\":\"success\",\"fileMd5\":\"" + MD5.toUpperCase() + "\"}");

        assertThat(payload).isNotNull();
        assertThat(payload.fileMd5()).isEqualTo(MD5);
    }

    @Test
    void payload_treats_malformed_md5_as_not_reported() {
        // 长度不对 / 非十六进制：一律按未回传处理，不能让可选字段把老设备的升级卡死
        assertThat(parsedMd5("short")).isNull();
        assertThat(parsedMd5("z".repeat(32))).isNull();
        assertThat(parsedMd5("g".repeat(32))).as("32 位非十六进制仍按未回传处理").isNull();
        assertThat(parsedMd5(MD5.toUpperCase())).as("大写十六进制归一化为小写").isEqualTo(MD5);
    }

    // ================= stub / helper =================

    private void stubCreateBasics(List<Device> targets) {
        OtaFirmware firmware = firmware();
        BatchTargetRequest target = target();
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware);
        when(deviceBatchService.resolveTarget(USER_ID, target)).thenReturn(targets);
        when(taskMapper.insert(any(OtaUpgradeTask.class))).thenAnswer(invocation -> {
            OtaUpgradeTask t = invocation.getArgument(0);
            t.setId(TASK_ID);
            return 1;
        });
        when(batchMapper.insert(any(OtaUpgradeBatch.class))).thenAnswer(invocation -> {
            OtaUpgradeBatch b = invocation.getArgument(0);
            b.setId((long) (insertedBatches.size() + 1));
            insertedBatches.add(b);
            return 1;
        });
        when(recordMapper.insert(any(OtaUpgradeRecord.class))).thenAnswer(invocation -> {
            OtaUpgradeRecord r = invocation.getArgument(0);
            // 主键从 1000 起：避免与 TASK_ID 混淆，使下方「按参数表过滤」能唯一还原 SQL 的 IN 过滤
            r.setId((long) (insertedRecords.size() + 1000));
            insertedRecords.add(r);
            return 1;
        });
        when(taskMapper.selectById(TASK_ID)).thenAnswer(invocation -> task());
        when(firmwareMapper.selectBatchIds(any())).thenReturn(List.of(firmware));
        when(productService.list()).thenReturn(List.of());
        when(deviceCommandService.invoke(any())).thenAnswer(invocation -> command("cmd-" + System.nanoTime()));
        when(taskMapper.refreshCounters(anyLong())).thenReturn(1);
        when(batchMapper.refreshBatchCounters(anyLong(), any())).thenReturn(1);
        // Mockito 对 List 返回类型默认给空集合，这里显式声明意图：默认无冲突设备
        when(recordMapper.selectActiveDeviceIdsExcludingTask(any(), anyLong())).thenReturn(List.of());
        // 建任务后 dispatchTask 要按 recordIds 回查 PENDING 记录。mock 不会真正执行 SQL，
        // 故用「查询参数里点名的记录主键」还原 IN 过滤——否则灰度「只发首批」根本无从断言。
        when(recordMapper.selectList(any())).thenAnswer(invocation -> {
            AbstractWrapper<?, ?, ?> wrapper = invocation.getArgument(0);
            // MyBatis-Plus 的条件值挂在 appendSqlSegments 的 supplier 上，必须先渲染 SQL 才会入表
            wrapper.getSqlSegment();
            java.util.Set<Object> wanted = new java.util.HashSet<>(wrapper.getParamNameValuePairs().values());
            return insertedRecords.stream()
                    .filter(r -> wanted.contains(r.getId()))
                    .collect(java.util.stream.Collectors.toList());
        });
        when(recordMapper.update(any(), any())).thenReturn(2);
    }

    private void stubProgress(OtaUpgradeRecord active, int progress, String firmwareMd5) {
        active.setProgress(progress);
        when(recordMapper.selectLatestActiveByDevice(anyLong())).thenReturn(active);
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(firmwareMd5));
    }

    private OtaFirmware firmware() {
        return firmware(MD5);
    }

    private OtaFirmware firmware(String md5) {
        OtaFirmware f = new OtaFirmware();
        f.setId(FIRMWARE_ID);
        f.setUserId(USER_ID);
        f.setProductId(PRODUCT_ID);
        f.setVersion(VERSION);
        f.setFilePath("3/1.0.0/fw.bin");
        f.setFileSize(4096L);
        f.setMd5(md5);
        return f;
    }

    private OtaUpgradeTask task() {
        OtaUpgradeTask t = new OtaUpgradeTask();
        t.setId(TASK_ID);
        t.setUserId(USER_ID);
        t.setFirmwareId(FIRMWARE_ID);
        t.setProductId(PRODUCT_ID);
        t.setName("整批升级");
        t.setTotalCount(2);
        t.setDispatchedCount(0);
        t.setSuccessCount(0);
        t.setFailedCount(0);
        t.setSkippedCount(0);
        t.setStatus(OtaStatusValue.TASK_RUNNING);
        t.setBatchMode(OtaStatusValue.BATCH_MODE_ALL);
        t.setBatchSize(0);
        t.setBatchIntervalMs(0L);
        t.setBatchTotal(1);
        t.setCurrentBatchNo(1);
        t.setCreatedAt(LocalDateTime.of(2026, 10, 10, 9, 0, 0));
        return t;
    }

    private OtaUpgradeBatch batch(Long id, int batchNo) {
        OtaUpgradeBatch b = new OtaUpgradeBatch();
        b.setId(id);
        b.setTaskId(TASK_ID);
        b.setBatchNo(batchNo);
        b.setDeviceCount(2);
        b.setDispatchedCount(0);
        b.setSuccessCount(0);
        b.setFailedCount(0);
        b.setSkippedCount(0);
        b.setStatus(OtaStatusValue.BATCH_PENDING);
        return b;
    }

    private OtaUpgradeRecord record(String status) {
        OtaUpgradeRecord r = recordInBatch(1L, 1);
        r.setStatus(status);
        return r;
    }

    /** 灰度场景：显式指定设备与所属批次，避免与按主键取记录的重载混淆。 */
    private OtaUpgradeRecord recordInBatch(Long deviceId, int batchNo) {
        OtaUpgradeRecord r = new OtaUpgradeRecord();
        r.setId(1L);
        r.setTaskId(TASK_ID);
        r.setDeviceId(deviceId);
        r.setFirmwareId(FIRMWARE_ID);
        r.setVersion(VERSION);
        r.setBatchNo(batchNo);
        r.setStatus(OtaStatusValue.RECORD_PENDING);
        r.setProgress(0);
        r.setVerifyStatus(OtaStatusValue.VERIFY_NOT_REPORTED);
        return r;
    }

    private OtaUpgradeRecord recordWithId(Long id, Long deviceId) {
        OtaUpgradeRecord r = recordInBatch(deviceId, 1);
        r.setId(id);
        return r;
    }

    /** 解析一行 success 报文并取出归一化后的 md5（{@code null} 表示按未回传处理）。 */
    private String parsedMd5(String raw) {
        return OtaProgressPayload.parse(new tools.jackson.databind.ObjectMapper(),
                "{\"version\":\"" + VERSION + "\",\"status\":\"success\",\"fileMd5\":\"" + raw + "\"}").fileMd5();
    }

    private Device device(Long id) {
        Device d = new Device();
        d.setId(id);
        d.setProductId(PRODUCT_ID);
        d.setDeviceName("设备" + id);
        d.setDeviceKey("key-" + id);
        return d;
    }

    private DeviceCommandRecord command(String commandId) {
        DeviceCommandRecord c = new DeviceCommandRecord();
        c.setCommandId(commandId);
        return c;
    }

    private BatchTargetRequest target() {
        BatchTargetRequest t = new BatchTargetRequest();
        t.setDeviceIds(List.of(1L, 2L));
        return t;
    }

    private OtaTaskCreateRequest request(String name, String batchMode, Integer batchSize, Long intervalMs) {
        OtaTaskCreateRequest req = new OtaTaskCreateRequest();
        req.setName(name);
        req.setFirmwareId(FIRMWARE_ID);
        req.setTarget(target());
        req.setBatchMode(batchMode);
        req.setBatchSize(batchSize);
        req.setBatchIntervalMs(intervalMs);
        return req;
    }

    private String otaPayload(String status, String fileMd5) {
        return otaPayload(status, fileMd5, null);
    }

    private String otaPayload(String status, String fileMd5, Integer progress) {
        StringBuilder sb = new StringBuilder("{\"version\":\"").append(VERSION)
                .append("\",\"status\":\"").append(status).append("\"");
        if (fileMd5 != null) {
            sb.append(",\"fileMd5\":\"").append(fileMd5).append("\"");
        }
        if (progress != null) {
            sb.append(",\"progress\":").append(progress);
        }
        return sb.append("}").toString();
    }

    private ResolvedEvent event(String payload) {
        Device d = device(1L);
        return new ResolvedEvent(d, new IngestRecord(d.getDeviceKey(), "device/" + d.getDeviceKey() + "/ota",
                "ota", payload, 1, LocalDateTime.now()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureRecordUpdateParams() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(any(), captor.capture());
        captor.getValue().getSqlSet();
        return captor.getValue().getParamNameValuePairs();
    }

    @SuppressWarnings("unchecked")
    private String captureRecordUpdateSql() {
        return captureRecordUpdateSqls().get(captureRecordUpdateSqls().size() - 1);
    }

    @SuppressWarnings("unchecked")
    private List<String> captureRecordUpdateSqls() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper, atLeastOnce()).update(any(), captor.capture());
        List<String> sqls = new ArrayList<>();
        for (LambdaUpdateWrapper<OtaUpgradeRecord> wrapper : captor.getAllValues()) {
            wrapper.getSqlSet();
            sqls.add(wrapper.getSqlSet());
        }
        return sqls;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureTaskUpdateParams() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeTask>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(taskMapper, atLeastOnce()).update(any(), captor.capture());
        LambdaUpdateWrapper<OtaUpgradeTask> last = captor.getAllValues()
                .get(captor.getAllValues().size() - 1);
        last.getSqlSet();
        return last.getParamNameValuePairs();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureRecordUpdateParamsList() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper, atLeastOnce()).update(any(), captor.capture());
        LambdaUpdateWrapper<OtaUpgradeRecord> last = captor.getAllValues()
                .get(captor.getAllValues().size() - 1);
        last.getSqlSet();
        return last.getParamNameValuePairs();
    }

    private OtaUpgradeTask captureInsertedTask() {
        ArgumentCaptor<OtaUpgradeTask> captor = ArgumentCaptor.forClass(OtaUpgradeTask.class);
        verify(taskMapper).insert(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBatchUpdateParams() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeBatch>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(batchMapper, atLeastOnce()).update(any(), captor.capture());
        LambdaUpdateWrapper<OtaUpgradeBatch> last = captor.getAllValues()
                .get(captor.getAllValues().size() - 1);
        last.getSqlSet();
        return last.getParamNameValuePairs();
    }

    @SuppressWarnings("unchecked")
    private String captureTaskUpdateSql() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeTask>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(taskMapper).update(any(), captor.capture());
        return captor.getValue().getSqlSet();
    }

    @SuppressWarnings("unchecked")
    private String captureBatchUpdateSql() {
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeBatch>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(batchMapper).update(any(), captor.capture());
        return captor.getValue().getSqlSet();
    }
}