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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理控制器
 */
@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceStatusHistoryService deviceStatusHistoryService;

    public DeviceController(DeviceService deviceService, DeviceStatusHistoryService deviceStatusHistoryService) {
        this.deviceService = deviceService;
        this.deviceStatusHistoryService = deviceStatusHistoryService;
    }

    @PostMapping
    public Result<Device> createDevice(@Valid @RequestBody CreateDeviceDTO dto) {
        Device device = deviceService.createDevice(SecurityUtils.requireUserId(), dto);
        return Result.success(device);
    }

    @GetMapping
    public Result<IPage<Device>> getDevices(DeviceQueryDTO dto) {
        // ADMIN 可查看全部设备，其他角色仅能查看自己的设备
        if (!SecurityUtils.isAdmin()) {
            dto.setOwnerId(SecurityUtils.requireUserId());
        }
        IPage<Device> page = deviceService.getDevices(SecurityUtils.requireUserId(), dto);
        return Result.success(page);
    }

    @GetMapping("/{deviceId}")
    public Result<Device> getDeviceDetail(@PathVariable Long deviceId) {
        checkDeviceOwnership(deviceId);
        return Result.success(deviceService.getDeviceById(deviceId));
    }

    @PutMapping("/{deviceId}")
    public Result<Device> updateDevice(@PathVariable Long deviceId,
                                       @Valid @RequestBody UpdateDeviceDTO dto) {
        checkDeviceOwnership(deviceId);
        return Result.success(deviceService.updateDevice(deviceId, dto));
    }

    @DeleteMapping("/{deviceId}")
    public Result<Void> deleteDevice(@PathVariable Long deviceId) {
        checkDeviceOwnership(deviceId);
        deviceService.deleteDevice(deviceId);
        return Result.success();
    }

    @GetMapping("/online")
    public Result<List<Device>> getOnlineDevices() {
        return Result.success(deviceService.getOnlineDevices(SecurityUtils.requireUserId()));
    }

    @GetMapping("/{deviceId}/status")
    public Result<List<DeviceStatus>> getDeviceStatusHistory(@PathVariable Long deviceId) {
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