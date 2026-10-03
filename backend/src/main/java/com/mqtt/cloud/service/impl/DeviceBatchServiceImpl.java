package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.BatchAssignRequest;
import com.mqtt.cloud.dto.request.BatchCommandRequest;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.response.BatchOperationResult;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 设备批量操作服务实现（T-18 设计文档 §8）。
 * <p>
 * 目标集合解析见 {@link #resolveTarget}：手选 ∪ 产品 ∪ 分组（含子分组）∪ 标签，去重后按当前用户过滤；
 * 逐台操作相互隔离，单台失败只记该台 {@code error}，不阻断整批。
 */
@Slf4j
@Service
public class DeviceBatchServiceImpl implements DeviceBatchService {

    private final DeviceGroupService deviceGroupService;
    private final DeviceTagService deviceTagService;
    private final DeviceService deviceService;
    private final DeviceCommandService deviceCommandService;

    public DeviceBatchServiceImpl(DeviceGroupService deviceGroupService,
                                  DeviceTagService deviceTagService,
                                  DeviceService deviceService,
                                  DeviceCommandService deviceCommandService) {
        this.deviceGroupService = deviceGroupService;
        this.deviceTagService = deviceTagService;
        this.deviceService = deviceService;
        this.deviceCommandService = deviceCommandService;
    }

    @Override
    public BatchOperationResult sendCommands(Long userId, BatchCommandRequest request) {
        List<Device> targets = resolveTarget(userId, request);
        List<BatchOperationResult.Item> items = new ArrayList<>();
        for (Device device : targets) {
            try {
                // 批量强制异步：同步需逐台阻塞等待回执，500 台上限下不可接受
                DeviceCommandRecord record = deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                        device.getId(), device.getProductId(), request.getType(), request.getIdentifier(),
                        request.getParams() == null ? null : request.getParams().toString(),
                        DeviceCommandService.CALL_TYPE_ASYNC, DeviceCommandService.SOURCE_CONSOLE, userId));
                items.add(BatchOperationResult.Item.ok(device.getId(), device.getDeviceKey(),
                        device.getDeviceName(), record.getCommandId(), record.getStatus()));
            } catch (BusinessException e) {
                items.add(BatchOperationResult.Item.fail(device.getId(), device.getDeviceKey(),
                        device.getDeviceName(), e.getMessage()));
            } catch (Exception e) {
                log.warn("批量下发命令异常: deviceId={}", device.getId(), e);
                items.add(BatchOperationResult.Item.fail(device.getId(), device.getDeviceKey(),
                        device.getDeviceName(), "下发失败：" + e.getMessage()));
            }
        }
        return BatchOperationResult.of(items);
    }

    @Override
    public BatchOperationResult setEnabled(Long userId, BatchTargetRequest target, boolean enabled) {
        List<Device> targets = resolveTarget(userId, target);
        List<BatchOperationResult.Item> items = new ArrayList<>();
        for (Device device : targets) {
            try {
                if (enabled) {
                    deviceService.enableDevice(device.getId());
                } else {
                    deviceService.disableDevice(device.getId());
                }
                items.add(BatchOperationResult.Item.ok(device.getId(), device.getDeviceKey(),
                        device.getDeviceName(), null, null));
            } catch (BusinessException e) {
                items.add(BatchOperationResult.Item.fail(device.getId(), device.getDeviceKey(),
                        device.getDeviceName(), e.getMessage()));
            } catch (Exception e) {
                log.warn("批量{}设备异常: deviceId={}", enabled ? "启用" : "禁用", device.getId(), e);
                items.add(BatchOperationResult.Item.fail(device.getId(), device.getDeviceKey(),
                        device.getDeviceName(), "操作失败：" + e.getMessage()));
            }
        }
        return BatchOperationResult.of(items);
    }

    @Override
    public BatchOperationResult assignGroup(Long userId, BatchAssignRequest request, boolean add) {
        if (request == null || request.getGroupId() == null) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
        }
        deviceGroupService.getOwned(userId, request.getGroupId());
        List<Device> targets = resolveTarget(userId, request);
        List<Long> deviceIds = targets.stream().map(Device::getId).toList();
        if (add) {
            deviceGroupService.addDevices(userId, request.getGroupId(), deviceIds);
        } else {
            deviceGroupService.removeDevices(userId, request.getGroupId(), deviceIds);
        }
        return successOf(targets);
    }

    @Override
    public BatchOperationResult assignTag(Long userId, BatchAssignRequest request, boolean add) {
        if (request == null || request.getTagId() == null) {
            throw new BusinessException(ResultCode.DEVICE_TAG_NOT_FOUND);
        }
        deviceTagService.getOwned(userId, request.getTagId());
        List<Device> targets = resolveTarget(userId, request);
        List<Long> deviceIds = targets.stream().map(Device::getId).toList();
        if (add) {
            deviceTagService.addDevices(userId, request.getTagId(), deviceIds);
        } else {
            deviceTagService.removeDevices(userId, request.getTagId(), deviceIds);
        }
        return successOf(targets);
    }

    @Override
    public List<Device> resolveTarget(Long userId, BatchTargetRequest target) {
        if (target == null) {
            throw new BusinessException(ResultCode.BATCH_TARGET_INVALID, "批量操作目标不能为空");
        }
        Set<Long> deviceIds = new HashSet<>();
        if (target.getDeviceIds() != null) {
            target.getDeviceIds().stream().filter(Objects::nonNull).forEach(deviceIds::add);
        }

        // 产品维度（T-22）：取该产品下当前用户的设备；为空即跳过，既有三维行为完全不变
        List<Long> productIds = distinct(target.getProductIds());
        if (!productIds.isEmpty()) {
            deviceService.list(Wrappers.<Device>lambdaQuery()
                            .in(Device::getProductId, productIds)
                            .eq(Device::getOwnerId, userId))
                    .forEach(device -> deviceIds.add(device.getId()));
        }

        List<Long> groupIds = distinct(target.getGroupIds());
        if (!groupIds.isEmpty()) {
            List<Long> expanded = new ArrayList<>();
            for (Long groupId : groupIds) {
                deviceGroupService.getOwned(userId, groupId);
                expanded.addAll(deviceGroupService.descendantIds(userId, groupId));
            }
            deviceIds.addAll(deviceGroupService.deviceIdsByGroupIds(expanded));
        }

        List<Long> tagIds = distinct(target.getTagIds());
        if (!tagIds.isEmpty()) {
            for (Long tagId : tagIds) {
                deviceTagService.getOwned(userId, tagId);
            }
            deviceIds.addAll(deviceTagService.deviceIdsByTagIds(tagIds));
        }

        if (deviceIds.isEmpty()) {
            throw new BusinessException(ResultCode.BATCH_TARGET_INVALID, "批量操作目标不能为空");
        }
        // 按当前用户二次过滤：即使前端传了他人设备 ID 也会被静默剔除；逻辑删除设备由 listByIds 自动排除
        List<Device> devices = deviceService.listByIds(deviceIds).stream()
                .filter(device -> userId.equals(device.getOwnerId()))
                .sorted(Comparator.comparing(Device::getId))
                .toList();
        if (devices.isEmpty()) {
            throw new BusinessException(ResultCode.BATCH_TARGET_INVALID, "批量操作目标不能为空");
        }
        if (devices.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.BATCH_TARGET_INVALID,
                    "批量操作目标超过上限 " + MAX_BATCH_SIZE);
        }
        return devices;
    }

    private BatchOperationResult successOf(List<Device> targets) {
        List<BatchOperationResult.Item> items = targets.stream()
                .map(device -> BatchOperationResult.Item.ok(device.getId(), device.getDeviceKey(),
                        device.getDeviceName(), null, null))
                .toList();
        return BatchOperationResult.of(items);
    }

    private List<Long> distinct(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }
}