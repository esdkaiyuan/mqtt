package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.OtaStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.OtaProperties;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.request.OtaTaskCreateRequest;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import com.mqtt.cloud.entity.OtaUpgradeTask;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.OtaFirmwareMapper;
import com.mqtt.cloud.mapper.OtaUpgradeRecordMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.ProductService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OTA 升级任务服务单测（T-22 实施计划 P6）。
 * <p>
 * 覆盖：目标解析后按固件产品过滤（不匹配即 {@code 6237}）、目标超 {@code task-max-devices}（{@code 6237}）、
 * 固件归属（{@code 6231}）、任务归属（{@code 6236}）、逐台下发参数（{@code url/version/md5/size}）装配、
 * 单台失败保持 {@code PENDING} 且不计数、{@code message} 截断、重投状态守卫（{@code 6238}）、
 * 删除状态守卫、分页收敛（{@code MAX_PAGE_SIZE}）、补投按任务分组、超时置 {@code TIMEOUT}。
 */
class OtaUpgradeServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long PRODUCT_ID = 3L;
    private static final Long OTHER_PRODUCT_ID = 8L;
    private static final Long FIRMWARE_ID = 1L;
    private static final Long TASK_ID = 5L;
    private static final Long DEVICE_ID_1 = 101L;
    private static final Long DEVICE_ID_2 = 102L;
    private static final Long DEVICE_ID_3 = 103L;
    private static final String VERSION = "1.0.0";
    private static final String FILE_PATH = "3/1.0.0/fw.bin";
    private static final String MD5 = "d41d8cd98f00b204e9800998ecf8427e";

    private OtaFirmwareMapper firmwareMapper;
    private OtaUpgradeTaskMapper taskMapper;
    private OtaUpgradeRecordMapper recordMapper;
    private DeviceMapper deviceMapper;
    private DeviceBatchService deviceBatchService;
    private DeviceCommandService deviceCommandService;
    private ProductService productService;
    private OtaProperties properties;
    private ObjectMapper objectMapper;
    private OtaUpgradeServiceImpl service;

    private final List<OtaUpgradeRecord> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OtaUpgradeRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OtaUpgradeTask.class);
        firmwareMapper = mock(OtaFirmwareMapper.class);
        taskMapper = mock(OtaUpgradeTaskMapper.class);
        recordMapper = mock(OtaUpgradeRecordMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        deviceBatchService = mock(DeviceBatchService.class);
        deviceCommandService = mock(DeviceCommandService.class);
        productService = mock(ProductService.class);
        properties = new OtaProperties();
        objectMapper = new ObjectMapper();
        service = new OtaUpgradeServiceImpl(firmwareMapper, taskMapper, recordMapper, deviceMapper,
                deviceBatchService, deviceCommandService, productService, properties, objectMapper);
    }

    // ---------- 任务创建 ----------

    @Test
    void createTask_should_insert_records_for_matched_devices_and_dispatch() throws Exception {
        OtaFirmware firmware = firmware(FIRMWARE_ID, USER_ID, PRODUCT_ID);
        BatchTargetRequest target = target();
        OtaTaskCreateRequest request = request("整批升级", FIRMWARE_ID, target);

        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware);
        when(deviceBatchService.resolveTarget(USER_ID, target)).thenReturn(List.of(
                device(DEVICE_ID_1, PRODUCT_ID), device(DEVICE_ID_2, PRODUCT_ID), device(DEVICE_ID_3, OTHER_PRODUCT_ID)));
        when(taskMapper.insert(any(OtaUpgradeTask.class))).thenAnswer(invocation -> {
            OtaUpgradeTask task = invocation.getArgument(0);
            task.setId(TASK_ID);
            return 1;
        });
        when(recordMapper.insert(any(OtaUpgradeRecord.class))).thenAnswer(invocation -> {
            OtaUpgradeRecord record = invocation.getArgument(0);
            record.setId((long) (inserted.size() + 1));
            inserted.add(record);
            return 1;
        });
        when(taskMapper.selectById(TASK_ID)).thenAnswer(invocation -> task());
        when(recordMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(inserted));
        when(deviceCommandService.invoke(any())).thenAnswer(invocation -> command("cmd"));
        when(firmwareMapper.selectBatchIds(any())).thenReturn(List.of(firmware));
        when(productService.list()).thenReturn(List.of(product(PRODUCT_ID, "温湿度传感器")));
        when(taskMapper.refreshCounters(TASK_ID)).thenReturn(2);

        OtaTaskDetailVO vo = service.createTask(USER_ID, request);

        // 仅固件产品一致的设备建记录，其它产品设备被剔除
        assertThat(inserted).hasSize(2);
        assertThat(inserted).extracting(OtaUpgradeRecord::getDeviceId)
                .containsExactly(DEVICE_ID_1, DEVICE_ID_2);
        assertThat(inserted).allSatisfy(record -> {
            assertThat(record.getTaskId()).isEqualTo(TASK_ID);
            assertThat(record.getFirmwareId()).isEqualTo(FIRMWARE_ID);
            assertThat(record.getVersion()).isEqualTo(VERSION);
            assertThat(record.getStatus()).isEqualTo(OtaStatusValue.RECORD_PENDING);
            assertThat(record.getProgress()).isZero();
        });

        assertThat(vo.getId()).isEqualTo(TASK_ID);
        assertThat(vo.getVersion()).isEqualTo(VERSION);
        assertThat(vo.getProductName()).isEqualTo("温湿度传感器");
        verify(taskMapper).refreshCounters(TASK_ID);

        // 下发入参：服务调用 + 固件地址 / 版本 / MD5 / 大小
        ArgumentCaptor<DeviceCommandService.CommandInvoke> captor =
                ArgumentCaptor.forClass(DeviceCommandService.CommandInvoke.class);
        verify(deviceCommandService, times(2)).invoke(captor.capture());
        DeviceCommandService.CommandInvoke invoke = captor.getValue();
        assertThat(invoke.commandType()).isEqualTo(DeviceCommandService.TYPE_SERVICE);
        assertThat(invoke.identifier()).isEqualTo(DeviceCommandService.SERVICE_OTA_UPGRADE);
        assertThat(invoke.callType()).isEqualTo(DeviceCommandService.CALL_TYPE_ASYNC);
        assertThat(invoke.source()).isEqualTo(DeviceCommandService.SOURCE_OTA);
        assertThat(invoke.operatorId()).isEqualTo(USER_ID);
        JsonNode params = objectMapper.readTree(invoke.paramsJson());
        assertThat(params.get("url").asText()).isEqualTo("http://localhost/firmware/3/1.0.0/fw.bin");
        assertThat(params.get("version").asText()).isEqualTo(VERSION);
        assertThat(params.get("md5").asText()).isEqualTo(MD5);
        assertThat(params.get("size").asLong()).isEqualTo(4096L);
    }

    @Test
    void createTask_should_reject_when_no_device_matches_firmware_product() {
        BatchTargetRequest target = target();
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID, OTHER_PRODUCT_ID));
        when(deviceBatchService.resolveTarget(USER_ID, target)).thenReturn(List.of(device(DEVICE_ID_1, PRODUCT_ID)));

        assertCode(() -> service.createTask(USER_ID, request("整批升级", FIRMWARE_ID, target)),
                ResultCode.OTA_TASK_INVALID);
        verify(taskMapper, never()).insert(any(OtaUpgradeTask.class));
    }

    @Test
    void createTask_should_reject_when_target_exceeds_max_devices() {
        properties.setTaskMaxDevices(1);
        BatchTargetRequest target = target();
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID, PRODUCT_ID));
        when(deviceBatchService.resolveTarget(USER_ID, target)).thenReturn(List.of(
                device(DEVICE_ID_1, PRODUCT_ID), device(DEVICE_ID_2, PRODUCT_ID)));

        assertCode(() -> service.createTask(USER_ID, request("整批升级", FIRMWARE_ID, target)),
                ResultCode.OTA_TASK_INVALID);
        verify(taskMapper, never()).insert(any(OtaUpgradeTask.class));
    }

    @Test
    void createTask_should_reject_when_disabled() {
        properties.setEnabled(false);

        assertCode(() -> service.createTask(USER_ID, request("整批升级", FIRMWARE_ID, target())),
                ResultCode.OTA_FIRMWARE_INVALID);
        verifyNoInteractions(firmwareMapper, deviceBatchService);
    }

    @Test
    void createTask_should_reject_blank_request() {
        assertCode(() -> service.createTask(USER_ID, null), ResultCode.OTA_TASK_INVALID);
    }

    @Test
    void createTask_should_reject_firmware_not_owned() {
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, OTHER_USER_ID, PRODUCT_ID));

        assertCode(() -> service.createTask(USER_ID, request("整批升级", FIRMWARE_ID, target())),
                ResultCode.OTA_FIRMWARE_NOT_FOUND);
        assertCode(() -> service.createTask(USER_ID, request("整批升级", null, target())),
                ResultCode.OTA_FIRMWARE_NOT_FOUND);
    }

    // ---------- 查询与分页 ----------

    @Test
    void listTasks_should_clamp_page_and_size() {
        when(taskMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productService.list()).thenReturn(List.of());

        service.listTasks(USER_ID, 0, 1000);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<OtaUpgradeTask>> captor = ArgumentCaptor.forClass(Page.class);
        verify(taskMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(100);
    }

    @Test
    void listRecords_should_require_owned_task() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertCode(() -> service.listRecords(USER_ID, TASK_ID, null, 1, 10), ResultCode.OTA_TASK_NOT_FOUND);
    }

    @Test
    void detail_should_throw_when_task_not_owned() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(taskWithUser(OTHER_USER_ID));

        assertCode(() -> service.detail(USER_ID, TASK_ID), ResultCode.OTA_TASK_NOT_FOUND);
        assertCode(() -> service.detail(USER_ID, null), ResultCode.OTA_TASK_NOT_FOUND);
    }

    // ---------- 重投与删除 ----------

    @Test
    void retry_should_reject_success_task() {
        OtaUpgradeTask task = taskWithUser(USER_ID);
        task.setStatus(OtaStatusValue.TASK_SUCCESS);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertCode(() -> service.retry(USER_ID, TASK_ID), ResultCode.OTA_TASK_STATE_INVALID);
        verifyNoInteractions(recordMapper);
    }

    @Test
    void retry_should_return_zero_when_no_retryable_records() {
        OtaUpgradeTask task = taskWithUser(USER_ID);
        task.setStatus(OtaStatusValue.TASK_PARTIAL);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.retry(USER_ID, TASK_ID)).isZero();
        verify(recordMapper, never()).update(any(), any());
        verify(deviceCommandService, never()).invoke(any());
    }

    @Test
    void retry_should_reset_progress_and_redispatch() {
        OtaUpgradeTask task = taskWithUser(USER_ID);
        task.setStatus(OtaStatusValue.TASK_PARTIAL);
        OtaUpgradeRecord record = record(1L, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_FAILED);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID, PRODUCT_ID));
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        when(deviceCommandService.invoke(any())).thenReturn(command("cmd-retry"));
        when(taskMapper.refreshCounters(anyLong())).thenReturn(1);

        assertThat(service.retry(USER_ID, TASK_ID)).isEqualTo(1);

        // 重置待投的 update：进度归零 + 清空下发痕迹
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor = updateCaptor();
        verify(recordMapper, times(2)).update(any(), captor.capture());
        Map<String, Object> resetParams = updateParams(captor.getAllValues().get(0));
        assertThat(resetParams.values()).contains(OtaStatusValue.RECORD_PENDING, 0);
        assertThat(resetParams.values()).containsNull();
        verify(deviceCommandService).invoke(any());
        verify(taskMapper).refreshCounters(TASK_ID);
    }

    @Test
    void remove_should_reject_running_task() {
        OtaUpgradeTask task = taskWithUser(USER_ID);
        task.setStatus(OtaStatusValue.TASK_RUNNING);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertCode(() -> service.remove(USER_ID, TASK_ID), ResultCode.OTA_TASK_STATE_INVALID);
        verify(taskMapper, never()).deleteById(anyLong());
    }

    @Test
    void remove_should_physically_delete_finished_task() {
        OtaUpgradeTask task = taskWithUser(USER_ID);
        task.setStatus(OtaStatusValue.TASK_SUCCESS);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        service.remove(USER_ID, TASK_ID);

        verify(taskMapper).deleteById(TASK_ID);
    }

    // ---------- 下发与巡检 ----------

    @Test
    void dispatchTask_should_return_zero_for_empty_ids() {
        assertThat(service.dispatchTask(TASK_ID, List.of())).isZero();
        assertThat(service.dispatchTask(TASK_ID, null)).isZero();
        assertThat(service.dispatchTask(null, List.of(1L))).isZero();
        verifyNoInteractions(taskMapper, recordMapper);
    }

    @Test
    void dispatchTask_should_return_zero_when_task_or_firmware_missing() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);
        assertThat(service.dispatchTask(TASK_ID, List.of(1L))).isZero();

        when(taskMapper.selectById(TASK_ID)).thenReturn(taskWithUser(USER_ID));
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(null);
        assertThat(service.dispatchTask(TASK_ID, List.of(1L))).isZero();
        verify(deviceCommandService, never()).invoke(any());
    }

    @Test
    void dispatchTask_should_keep_pending_and_skip_count_on_single_failure() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(taskWithUser(USER_ID));
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID, PRODUCT_ID));
        when(recordMapper.selectList(any())).thenReturn(List.of(
                record(1L, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_PENDING)));
        when(deviceCommandService.invoke(any())).thenThrow(new IllegalStateException("broker down"));

        assertThat(service.dispatchTask(TASK_ID, List.of(1L))).isZero();

        // 失败只写 message，不改状态（保持 PENDING 待补投）
        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor = updateCaptor();
        verify(recordMapper).update(any(), captor.capture());
        Map<String, Object> params = updateParams(captor.getValue());
        assertThat(params.values())
                .anySatisfy(value -> assertThat(String.valueOf(value)).startsWith("下发失败：broker down"));
        assertThat(params.values()).noneMatch(OtaStatusValue.RECORD_DISPATCHED::equals);
    }

    @Test
    void dispatchTask_should_truncate_failure_message_to_limit() {
        String longMessage = "x".repeat(400);
        when(taskMapper.selectById(TASK_ID)).thenReturn(taskWithUser(USER_ID));
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID, PRODUCT_ID));
        when(recordMapper.selectList(any())).thenReturn(List.of(
                record(1L, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_PENDING)));
        when(deviceCommandService.invoke(any())).thenThrow(new IllegalStateException(longMessage));

        service.dispatchTask(TASK_ID, List.of(1L));

        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor = updateCaptor();
        verify(recordMapper).update(any(), captor.capture());
        String written = updateParams(captor.getValue()).values().stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElseThrow();
        assertThat(written).hasSize(OtaUpgradeServiceImpl.ERROR_MESSAGE_MAX);
    }

    @Test
    void sweepPending_should_clamp_limit_and_group_by_task() {
        when(recordMapper.selectDispatchCandidates(eq(OtaStatusValue.RECORD_PENDING), anyInt()))
                .thenReturn(List.of(
                        record(1L, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_PENDING),
                        record(2L, 6L, DEVICE_ID_2, OtaStatusValue.RECORD_PENDING)));
        when(taskMapper.selectById(anyLong())).thenAnswer(invocation -> taskWithUser(USER_ID));
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID, PRODUCT_ID));
        // 两个分组各查一次待投记录：按调用顺序返回，避免固定列表使每组都循环到全部记录
        when(recordMapper.selectList(any()))
                .thenReturn(List.of(record(1L, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_PENDING)))
                .thenReturn(List.of(record(2L, 6L, DEVICE_ID_2, OtaStatusValue.RECORD_PENDING)));
        when(deviceCommandService.invoke(any())).thenReturn(command("cmd"));
        when(taskMapper.refreshCounters(anyLong())).thenReturn(1);

        assertThat(service.sweepPending(0)).isEqualTo(2);

        verify(recordMapper).selectDispatchCandidates(OtaStatusValue.RECORD_PENDING, 1);
        verify(taskMapper).refreshCounters(TASK_ID);
        verify(taskMapper).refreshCounters(6L);
    }

    @Test
    void sweepPending_should_return_zero_when_no_candidates() {
        when(recordMapper.selectDispatchCandidates(any(), anyInt())).thenReturn(List.of());

        assertThat(service.sweepPending(10)).isZero();
        verify(taskMapper, never()).refreshCounters(anyLong());
    }

    @Test
    void sweepTimeout_should_mark_stale_records_as_timeout() {
        when(recordMapper.selectList(any())).thenReturn(List.of(
                record(1L, TASK_ID, DEVICE_ID_1, OtaStatusValue.RECORD_DISPATCHED),
                record(2L, 6L, DEVICE_ID_2, OtaStatusValue.RECORD_FLASHING)));
        when(recordMapper.update(any(), any())).thenReturn(2);
        when(taskMapper.refreshCounters(anyLong())).thenReturn(1);

        assertThat(service.sweepTimeout(60_000L)).isEqualTo(2);

        ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> captor = updateCaptor();
        verify(recordMapper).update(any(), captor.capture());
        assertThat(updateParams(captor.getValue()).values())
                .contains(OtaStatusValue.RECORD_TIMEOUT, OtaUpgradeServiceImpl.TIMEOUT_MESSAGE);
        verify(taskMapper).refreshCounters(TASK_ID);
        verify(taskMapper).refreshCounters(6L);
    }

    @Test
    void sweepTimeout_should_return_zero_for_non_positive_or_empty() {
        assertThat(service.sweepTimeout(0)).isZero();
        verifyNoInteractions(recordMapper);

        when(recordMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.sweepTimeout(1000L)).isZero();
        verify(recordMapper, never()).update(any(), any());
    }

    // ---------- 辅助 ----------

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<LambdaUpdateWrapper<OtaUpgradeRecord>> updateCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }

    /**
     * 渲染 update wrapper 后再取参数表：MyBatis-Plus 的 {@code set} 值以惰性 ISqlSegment 挂载，
     * 必须先 {@code getSqlSet()} 求值，{@code getParamNameValuePairs()} 才会被填充。
     */
    private Map<String, Object> updateParams(LambdaUpdateWrapper<OtaUpgradeRecord> wrapper) {
        wrapper.getSqlSet();
        return wrapper.getParamNameValuePairs();
    }

    private BatchTargetRequest target() {
        BatchTargetRequest target = new BatchTargetRequest();
        target.setProductIds(List.of(PRODUCT_ID));
        return target;
    }

    private OtaTaskCreateRequest request(String name, Long firmwareId, BatchTargetRequest target) {
        OtaTaskCreateRequest request = new OtaTaskCreateRequest();
        request.setName(name);
        request.setFirmwareId(firmwareId);
        request.setTarget(target);
        return request;
    }

    private OtaFirmware firmware(Long id, Long userId, Long productId) {
        OtaFirmware firmware = new OtaFirmware();
        firmware.setId(id);
        firmware.setUserId(userId);
        firmware.setProductId(productId);
        firmware.setVersion(VERSION);
        firmware.setFileName("fw.bin");
        firmware.setFilePath(FILE_PATH);
        firmware.setFileSize(4096L);
        firmware.setMd5(MD5);
        return firmware;
    }

    private OtaUpgradeTask task() {
        OtaUpgradeTask task = taskWithUser(USER_ID);
        task.setFirmwareId(FIRMWARE_ID);
        task.setProductId(PRODUCT_ID);
        return task;
    }

    private OtaUpgradeTask taskWithUser(Long userId) {
        OtaUpgradeTask task = new OtaUpgradeTask();
        task.setId(TASK_ID);
        task.setUserId(userId);
        task.setFirmwareId(FIRMWARE_ID);
        task.setProductId(PRODUCT_ID);
        task.setName("整批升级");
        task.setTotalCount(2);
        task.setDispatchedCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setStatus(OtaStatusValue.TASK_RUNNING);
        task.setCreatedAt(LocalDateTime.of(2026, 10, 3, 9, 0, 0));
        return task;
    }

    private OtaUpgradeRecord record(Long id, Long taskId, Long deviceId, String status) {
        OtaUpgradeRecord record = new OtaUpgradeRecord();
        record.setId(id);
        record.setTaskId(taskId);
        record.setDeviceId(deviceId);
        record.setFirmwareId(FIRMWARE_ID);
        record.setVersion(VERSION);
        record.setStatus(status);
        record.setProgress(0);
        return record;
    }

    private Device device(Long id, Long productId) {
        Device device = new Device();
        device.setId(id);
        device.setProductId(productId);
        device.setDeviceName("设备" + id);
        device.setDeviceKey("key-" + id);
        return device;
    }

    private Product product(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setProductName(name);
        return product;
    }

    private DeviceCommandRecord command(String commandId) {
        DeviceCommandRecord command = new DeviceCommandRecord();
        command.setCommandId(commandId);
        return command;
    }
}
