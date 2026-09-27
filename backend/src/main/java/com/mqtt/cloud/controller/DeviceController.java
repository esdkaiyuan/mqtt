package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.request.UpdateDeviceDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceStatus;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理控制器
 */
@Tag(name = "设备管理", description = "设备增删改查、在线状态与状态变更历史")
@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceStatusHistoryService deviceStatusHistoryService;

    public DeviceController(DeviceService deviceService, DeviceStatusHistoryService deviceStatusHistoryService) {
        this.deviceService = deviceService;
        this.deviceStatusHistoryService = deviceStatusHistoryService;
    }

    @Operation(summary = "创建设备", description = "为当前用户创建设备，初始状态为 INACTIVE；deviceKey 重复返回 2001")
    @PostMapping
    public Result<Device> createDevice(@Valid @RequestBody CreateDeviceDTO dto) {
        Device device = deviceService.createDevice(SecurityUtils.requireUserId(), dto);
        return Result.success(device);
    }

    @Operation(summary = "获取设备列表", description = "分页查询设备；ADMIN 可见全部设备，其他角色仅可见自己名下设备")
    @GetMapping
    public Result<IPage<Device>> getDevices(DeviceQueryDTO dto) {
        // ADMIN 可查看全部设备，其他角色仅能查看自己的设备
        if (!SecurityUtils.isAdmin()) {
            dto.setOwnerId(SecurityUtils.requireUserId());
        }
        IPage<Device> page = deviceService.getDevices(SecurityUtils.requireUserId(), dto);
        return Result.success(page);
    }

    @Operation(summary = "获取设备详情", description = "按设备 ID 查询详情，非归属用户访问返回 2003")
    @GetMapping("/{deviceId}")
    public Result<Device> getDeviceDetail(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        checkDeviceOwnership(deviceId);
        return Result.success(deviceService.getDeviceById(deviceId));
    }

    @Operation(summary = "更新设备", description = "更新设备名称、类型、Topic 等可编辑字段")
    @PutMapping("/{deviceId}")
    public Result<Device> updateDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId,
            @Valid @RequestBody UpdateDeviceDTO dto) {
        checkDeviceOwnership(deviceId);
        return Result.success(deviceService.updateDevice(deviceId, dto));
    }

    @Operation(summary = "删除设备", description = "逻辑删除设备，删除后列表不再返回")
    @DeleteMapping("/{deviceId}")
    public Result<Void> deleteDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        checkDeviceOwnership(deviceId);
        deviceService.deleteDevice(deviceId);
        return Result.success();
    }

    @Operation(summary = "获取在线设备", description = "返回当前用户名下状态为 ONLINE 的设备列表")
    @GetMapping("/online")
    public Result<List<Device>> getOnlineDevices() {
        return Result.success(deviceService.getOnlineDevices(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "获取设备状态历史", description = "返回指定设备的状态变更记录列表")
    @GetMapping("/{deviceId}/status")
    public Result<List<DeviceStatus>> getDeviceStatusHistory(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        checkDeviceOwnership(deviceId);
        return Result.success(deviceStatusHistoryService.getHistoryByDeviceId(deviceId));
    }

    /**
     * 校验设备归属：ADMIN 可访问任意设备，其他角色仅能访问自己名下的设备。
     */
    private void checkDeviceOwnership(Long deviceId) {
        Device device = deviceService.getById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean isOwner = device.getOwnerId() != null && device.getOwnerId().equals(principal.getUserId());
        if (!"ADMIN".equals(principal.getRole()) && !isOwner) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
    }
}