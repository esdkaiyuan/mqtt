package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.BatchAssignRequest;
import com.mqtt.cloud.dto.request.BatchCommandRequest;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.response.BatchOperationResult;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备批量操作服务单测（T-18 实施计划 P5）。
 * <p>
 * 覆盖目标集合并集解析（手选 ∪ 分组含子分组 ∪ 标签、去重、他人设备剔除）、空目标 / 超上限（{@code 6217}）、
 * 批量命令逐台隔离、批量启用禁用、批量关联分组 / 标签。
 */
@ExtendWith(MockitoExtension.class)
class DeviceBatchServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private DeviceGroupService deviceGroupService;

    @Mock
    private DeviceTagService deviceTagService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceCommandService deviceCommandService;

    @InjectMocks
    private DeviceBatchServiceImpl service;

    @Test
    void resolveTarget_should_union_devices_groups_and_tags_deduped() {
        BatchTargetRequest target = new BatchTargetRequest();
        target.setDeviceIds(List.of(1L, 2L));
        target.setGroupIds(List.of(10L));
        target.setTagIds(List.of(20L));

        when(deviceGroupService.descendantIds(USER_ID, 10L)).thenReturn(List.of(10L, 11L));
        when(deviceGroupService.deviceIdsByGroupIds(any())).thenReturn(List.of(2L, 3L));
        when(deviceTagService.deviceIdsByTagIds(any())).thenReturn(List.of(3L, 4L));
        when(deviceService.listByIds(any())).thenReturn(List.of(
                device(1L, USER_ID), device(2L, USER_ID), device(3L, USER_ID), device(4L, USER_ID)));

        List<Device> result = service.resolveTarget(USER_ID, target);

        assertThat(result).extracting(Device::getId).containsExactly(1L, 2L, 3L, 4L);
        verify(deviceGroupService).getOwned(USER_ID, 10L);
        verify(deviceTagService).getOwned(USER_ID, 20L);
    }

    @Test
    void resolveTarget_should_reject_null_target() {
        assertCode(() -> service.resolveTarget(USER_ID, null), ResultCode.BATCH_TARGET_INVALID);
    }

    @Test
    void resolveTarget_should_reject_empty_target() {
        assertCode(() -> service.resolveTarget(USER_ID, new BatchTargetRequest()),
                ResultCode.BATCH_TARGET_INVALID);
    }

    @Test
    void resolveTarget_should_silently_drop_other_owners() {
        BatchTargetRequest target = new BatchTargetRequest();
        target.setDeviceIds(List.of(1L, 2L));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID), device(2L, 999L)));

        List<Device> result = service.resolveTarget(USER_ID, target);

        assertThat(result).extracting(Device::getId).containsExactly(1L);
    }

    @Test
    void resolveTarget_should_reject_when_all_targets_dropped() {
        BatchTargetRequest target = new BatchTargetRequest();
        target.setDeviceIds(List.of(2L));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(2L, 999L)));

        assertCode(() -> service.resolveTarget(USER_ID, target), ResultCode.BATCH_TARGET_INVALID);
    }

    @Test
    void resolveTarget_should_reject_over_max_batch_size() {
        List<Long> ids = IntStream.rangeClosed(1, 501).mapToObj(Long::valueOf).toList();
        BatchTargetRequest target = new BatchTargetRequest();
        target.setDeviceIds(ids);
        when(deviceService.listByIds(any())).thenReturn(ids.stream().map(id -> device(id, USER_ID)).toList());

        assertCode(() -> service.resolveTarget(USER_ID, target), ResultCode.BATCH_TARGET_INVALID);
    }

    @Test
    void sendCommands_should_isolate_per_device_failure() {
        BatchCommandRequest request = new BatchCommandRequest();
        request.setDeviceIds(List.of(1L, 2L));
        request.setType("property_set");
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID), device(2L, USER_ID)));

        DeviceCommandRecord sent = new DeviceCommandRecord();
        sent.setCommandId("cmd-1");
        sent.setStatus("SENT");
        when(deviceCommandService.invoke(any()))
                .thenReturn(sent)
                .thenThrow(new BusinessException(ResultCode.COMMAND_MODEL_MISSING));

        BatchOperationResult result = service.sendCommands(USER_ID, request);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items().get(0).commandId()).isEqualTo("cmd-1");
        assertThat(result.items().get(0).status()).isEqualTo("SENT");
        assertThat(result.items().get(1).success()).isFalse();
        assertThat(result.items().get(1).error()).contains("物模型");
    }

    @Test
    void sendCommands_should_capture_unexpected_exception() {
        BatchCommandRequest request = new BatchCommandRequest();
        request.setDeviceIds(List.of(1L));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID)));
        when(deviceCommandService.invoke(any())).thenThrow(new IllegalStateException("broker down"));

        BatchOperationResult result = service.sendCommands(USER_ID, request);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items().get(0).error()).contains("下发失败");
    }

    @Test
    void setEnabled_should_disable_each_target() {
        BatchTargetRequest target = new BatchTargetRequest();
        target.setDeviceIds(List.of(1L, 2L));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID), device(2L, USER_ID)));

        BatchOperationResult result = service.setEnabled(USER_ID, target, false);

        assertThat(result.succeeded()).isEqualTo(2);
        verify(deviceService).disableDevice(1L);
        verify(deviceService).disableDevice(2L);
    }

    @Test
    void setEnabled_should_isolate_failure_and_continue() {
        BatchTargetRequest target = new BatchTargetRequest();
        target.setDeviceIds(List.of(1L, 2L));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID), device(2L, USER_ID)));
        org.mockito.Mockito.doThrow(new BusinessException(ResultCode.DEVICE_NOT_FOUND))
                .when(deviceService).disableDevice(1L);

        BatchOperationResult result = service.setEnabled(USER_ID, target, false);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        verify(deviceService).disableDevice(2L);
    }

    @Test
    void assignGroup_should_add_devices() {
        BatchAssignRequest request = new BatchAssignRequest();
        request.setDeviceIds(List.of(1L));
        request.setGroupId(10L);
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID)));

        BatchOperationResult result = service.assignGroup(USER_ID, request, true);

        assertThat(result.succeeded()).isEqualTo(1);
        verify(deviceGroupService).getOwned(USER_ID, 10L);
        verify(deviceGroupService).addDevices(USER_ID, 10L, List.of(1L));
    }

    @Test
    void assignGroup_should_remove_devices() {
        BatchAssignRequest request = new BatchAssignRequest();
        request.setDeviceIds(List.of(1L));
        request.setGroupId(10L);
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID)));

        service.assignGroup(USER_ID, request, false);

        verify(deviceGroupService).removeDevices(USER_ID, 10L, List.of(1L));
    }

    @Test
    void assignGroup_should_reject_missing_group_id() {
        BatchAssignRequest request = new BatchAssignRequest();
        request.setDeviceIds(List.of(1L));

        assertCode(() -> service.assignGroup(USER_ID, request, true), ResultCode.DEVICE_GROUP_NOT_FOUND);
    }

    @Test
    void assignTag_should_add_devices() {
        BatchAssignRequest request = new BatchAssignRequest();
        request.setDeviceIds(List.of(1L));
        request.setTagId(20L);
        when(deviceService.listByIds(any())).thenReturn(List.of(device(1L, USER_ID)));

        service.assignTag(USER_ID, request, true);

        verify(deviceTagService).getOwned(USER_ID, 20L);
        verify(deviceTagService).addDevices(USER_ID, 20L, List.of(1L));
    }

    @Test
    void assignTag_should_reject_missing_tag_id() {
        BatchAssignRequest request = new BatchAssignRequest();
        request.setDeviceIds(List.of(1L));

        assertCode(() -> service.assignTag(USER_ID, request, true), ResultCode.DEVICE_TAG_NOT_FOUND);
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private Device device(Long id, Long ownerId) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerId(ownerId);
        device.setDeviceKey("dev-" + id);
        device.setDeviceName("设备-" + id);
        return device;
    }
}