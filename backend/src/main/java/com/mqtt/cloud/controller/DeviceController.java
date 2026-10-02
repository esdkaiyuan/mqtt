package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.CommandInvokeRequest;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.request.UpdateDeviceDTO;
import com.mqtt.cloud.dto.response.CommandCapabilityResponse;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.DeviceEventRecord;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.DeviceStatus;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final DeviceCredentialService deviceCredentialService;
    private final DeviceDataService deviceDataService;
    private final DeviceCommandService deviceCommandService;

    public DeviceController(DeviceService deviceService,
                            DeviceStatusHistoryService deviceStatusHistoryService,
                            DeviceCredentialService deviceCredentialService,
                            DeviceDataService deviceDataService,
                            DeviceCommandService deviceCommandService) {
        this.deviceService = deviceService;
        this.deviceStatusHistoryService = deviceStatusHistoryService;
        this.deviceCredentialService = deviceCredentialService;
        this.deviceDataService = deviceDataService;
        this.deviceCommandService = deviceCommandService;
    }

    @Operation(summary = "创建设备", description = "为当前用户创建设备，初始状态为 INACTIVE；"
            + "响应中的 deviceSecret 为一次性明文密钥，仅本次返回。deviceKey 在同一产品内重复返回 2001")
    @PostMapping
    public Result<DeviceCreatedDTO> createDevice(@Valid @RequestBody CreateDeviceDTO dto) {
        return Result.success(deviceService.createDevice(SecurityUtils.requireUserId(), dto));
    }

    @Operation(summary = "重置设备密钥", description = "重新签发一机一密密钥并返回一次性明文，旧密钥立即失效")
    @PostMapping("/{deviceId}/reset-secret")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> resetSecret(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        return Result.success(deviceCredentialService.resetSecret(deviceId));
    }

    @Operation(summary = "导出全部设备凭据", description = "一次性导出全部设备的 [productKey, deviceKey, username, secret]；"
            + "导出会重置密钥，旧密钥立即失效，请谨慎使用")
    @PostMapping("/export-credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<String[]>> exportCredentials() {
        return Result.success(deviceCredentialService.exportCredentials());
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

    @Operation(summary = "禁用设备", description = "将设备连接许可置为禁止，设备将在下次认证时被拒绝；重复禁用幂等成功")
    @PostMapping("/{deviceId}/disable")
    public Result<Void> disableDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        checkDeviceOwnership(deviceId);
        deviceService.disableDevice(deviceId);
        return Result.success();
    }

    @Operation(summary = "启用设备", description = "恢复设备连接许可，启用立即生效；重复启用幂等成功")
    @PostMapping("/{deviceId}/enable")
    public Result<Void> enableDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        checkDeviceOwnership(deviceId);
        deviceService.enableDevice(deviceId);
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

    @Operation(summary = "获取设备属性最新值", description = "按 deviceKey 返回物模型解析出的属性最新值列表；非归属用户访问返回 2003")
    @GetMapping("/{deviceKey}/properties")
    public Result<List<DevicePropertyLatest>> getDeviceProperties(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey);
        return Result.success(deviceDataService.getPropertyLatest(device.getId()));
    }

    @Operation(summary = "获取设备事件记录", description = "按 deviceKey 分页返回物模型解析出的事件记录，按上报时间倒序；非归属用户访问返回 2003")
    @GetMapping("/{deviceKey}/events")
    public Result<IPage<DeviceEventRecord>> getDeviceEvents(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        Device device = requireOwnedDevice(deviceKey);
        return Result.success(deviceDataService.getEvents(device.getId(), page, size));
    }

    @Operation(summary = "获取设备可下发能力", description = "返回产品物模型中可写属性与服务（含入参），供前端生成命令表单；"
            + "无物模型时 modeled=false 且两个集合为空；非归属用户访问返回 2003")
    @GetMapping("/{deviceKey}/command-capability")
    public Result<CommandCapabilityResponse> getCommandCapability(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey);
        return Result.success(toCapabilityResponse(deviceCommandService.getCapability(device.getProductId())));
    }

    @Operation(summary = "下发命令", description = "按物模型校验参数后下发到 device/{deviceKey}/cmd/down；"
            + "type=property_set 做属性设置、type=service 做服务调用；callType=sync 等待回执到终态。"
            + "物模型缺失 6201 / 属性不可写 6202 / 标识符未定义 6203 / 参数非法 6204 / 发布失败 4001")
    @PostMapping("/{deviceKey}/commands")
    public Result<DeviceCommandRecord> sendCommand(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Valid @RequestBody CommandInvokeRequest request) {
        Device device = requireOwnedDevice(deviceKey);
        DeviceCommandRecord record = deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                device.getId(), device.getProductId(), request.getType(), request.getIdentifier(),
                request.getParams() == null ? null : request.getParams().toString(),
                request.getCallType(), DeviceCommandService.SOURCE_CONSOLE, SecurityUtils.requireUserId()));
        return Result.success(record);
    }

    @Operation(summary = "获取命令记录", description = "按 deviceKey 分页返回命令记录，按创建时间倒序；非归属用户访问返回 2003")
    @GetMapping("/{deviceKey}/commands")
    public Result<IPage<DeviceCommandRecord>> getCommandRecords(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        Device device = requireOwnedDevice(deviceKey);
        return Result.success(deviceCommandService.listCommands(device.getId(), page, size));
    }

    /** 物模型能力投影 → 前端表单契约（只暴露可下发所需的字段）。 */
    private CommandCapabilityResponse toCapabilityResponse(DeviceCommandService.CommandCapability capability) {
        List<CommandCapabilityResponse.Property> properties = capability.properties().stream()
                .map(spec -> new CommandCapabilityResponse.Property(spec.identifier(), spec.type(), spec.min(),
                        spec.max(), spec.integer(), List.copyOf(spec.enumKeys()), spec.textLength()))
                .toList();
        List<CommandCapabilityResponse.Service> services = capability.services().stream()
                .map(service -> new CommandCapabilityResponse.Service(service.identifier(), service.callType(),
                        service.input().values().stream()
                                .map(param -> new CommandCapabilityResponse.Param(param.identifier(), param.type(),
                                        param.min(), param.max(), param.integer(), List.copyOf(param.enumKeys()),
                                        param.textLength(), param.required()))
                                .toList()))
                .toList();
        return new CommandCapabilityResponse(capability.modeled(), capability.version(), properties, services);
    }

    /**
     * 校验设备归属：ADMIN 可访问任意设备，其他角色仅能访问自己名下的设备。
     */
    private void checkDeviceOwnership(Long deviceId) {
        checkOwnership(deviceService.getById(deviceId));
    }

    /** 按 deviceKey 取设备并校验归属，供设备详情下的物模型数据只读接口使用。 */
    private Device requireOwnedDevice(String deviceKey) {
        Device device = deviceService.getDeviceByKey(deviceKey);
        checkOwnership(device);
        return device;
    }

    private void checkOwnership(Device device) {
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