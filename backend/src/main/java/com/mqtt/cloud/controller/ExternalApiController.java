package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.CommandInvokeRequest;
import com.mqtt.cloud.entity.ApiKey;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.ApiKeyService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceShadowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放 API（供第三方系统通过 API Key 调用）
 * <p>
 * 认证由 {@code ApiKeyAuthFilter} 完成，本控制器只负责数据隔离与业务处理：
 * 所有查询与操作都被限制在当前 API Key 所属用户的名下设备范围内。
 * <p>
 * 命令下发与控制台同语义（T-15 设计文档 §10.2），仅 {@code source=OPEN_API}、无操作者 ID；
 * 原 {@code {payload, qos, retain}} 直发契约因主题与 ACL 冲突本就不可用，已被结构化命令契约替换。
 */
@Slf4j
@Tag(name = "外部API", description = "供第三方系统调用的开放接口，使用 X-API-Key 请求头认证，数据范围限定为密钥所属用户")
@RestController
@RequestMapping("/external/v1")
@RequiredArgsConstructor
public class ExternalApiController {

    private final ApiKeyService apiKeyService;
    private final DeviceService deviceService;
    private final DeviceCommandService deviceCommandService;
    private final DeviceShadowService deviceShadowService;

    @Operation(summary = "外部-查询设备列表", description = "返回密钥所属用户的名下设备列表；传入 deviceKey 时精确返回单个设备")
    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices(
            @Parameter(description = "设备唯一标识，可选，传则精确查询单设备") @RequestParam(required = false) String deviceKey) {
        Long userId = currentUserId();
        if (deviceKey != null && !deviceKey.isBlank()) {
            return Result.success(List.of(toDeviceMap(requireOwnedDevice(deviceKey, userId))));
        }

        List<Map<String, Object>> devices = deviceService.getDevicesByOwner(userId).stream()
                .map(this::toDeviceMap)
                .toList();
        return Result.success(devices);
    }

    @Operation(summary = "外部-查询设备详情", description = "按 deviceKey 返回设备完整信息（含 description 与 metadata）")
    @GetMapping("/devices/{deviceKey}")
    public Result<Map<String, Object>> getDevice(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());

        Map<String, Object> result = toDeviceMap(device);
        result.put("description", device.getDescription());
        result.put("metadata", device.getMetadata());
        return Result.success(result);
    }

    @Operation(summary = "外部-查询设备状态", description = "返回设备的 status/lastSeen/online 字段")
    @GetMapping("/devices/{deviceKey}/status")
    public Result<Map<String, Object>> getDeviceStatus(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("deviceKey", device.getDeviceKey());
        result.put("status", device.getStatus());
        result.put("lastSeen", device.getLastSeen());
        result.put("online", "ONLINE".equals(device.getStatus()));
        return Result.success(result);
    }

    @Operation(summary = "外部-下发设备命令", description = "与控制台同语义：按物模型校验参数后下发到 device/{deviceKey}/cmd/down；"
            + "type=property_set / service，callType=sync 等待回执到终态。物模型缺失 6201 / 属性不可写 6202 / "
            + "标识符未定义 6203 / 参数非法 6204 / 发布失败 4001")
    @PostMapping("/devices/{deviceKey}/command")
    public Result<DeviceCommandRecord> sendCommand(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Valid @RequestBody CommandInvokeRequest request) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        DeviceCommandRecord record = deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                device.getId(), device.getProductId(), request.getType(), request.getIdentifier(),
                request.getParams() == null ? null : request.getParams().toString(),
                request.getCallType(), DeviceCommandService.SOURCE_OPEN_API, null));
        return Result.success(record);
    }

    @Operation(summary = "外部-查询命令记录", description = "按 deviceKey 分页返回命令记录，按创建时间倒序")
    @GetMapping("/devices/{deviceKey}/commands")
    public Result<IPage<DeviceCommandRecord>> getCommandRecords(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        return Result.success(deviceCommandService.listCommands(device.getId(), page, size));
    }

    @Operation(summary = "外部-查询设备影子", description = "返回 desired/reported/delta 三份状态与影子版本号；"
            + "影子不存在时返回空映射与 version=0；modeled=false 表示产品未定义物模型")
    @GetMapping("/devices/{deviceKey}/shadow")
    public Result<DeviceShadowService.DeviceShadowResponse> getDeviceShadow(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        return Result.success(deviceShadowService.get(device.getId(), device.getProductId()));
    }

    @Operation(summary = "外部-写入影子期望值", description = "等价于 type=property_set 命令（source=OPEN_API）；"
            + "在线设备返回 SENT，离线返回 QUEUED 并在上线后补发；params 缺失或为空返回 6206")
    @PutMapping("/devices/{deviceKey}/shadow/desired")
    public Result<DeviceCommandRecord> putDesired(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @RequestBody CommandInvokeRequest request) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        JsonNode params = request == null ? null : request.getParams();
        if (params == null || !params.isObject() || params.isEmpty()) {
            throw new BusinessException(ResultCode.SHADOW_DESIRED_INVALID, "params 必须为非空 JSON 对象");
        }
        return Result.success(deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                device.getId(), device.getProductId(), DeviceCommandService.TYPE_PROPERTY_SET, null,
                params.toString(), DeviceCommandService.CALL_TYPE_ASYNC,
                DeviceCommandService.SOURCE_OPEN_API, null)));
    }

    @Operation(summary = "外部-获取统计信息", description = "返回 totalDevices/onlineDevices/apiKeyName/permissions")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        List<Device> devices = deviceService.getDevicesByOwner(principal.getUserId());
        long onlineDevices = devices.stream().filter(d -> "ONLINE".equals(d.getStatus())).count();

        ApiKey apiKey = principal.getApiKeyId() != null ? apiKeyService.getById(principal.getApiKeyId()) : null;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDevices", (long) devices.size());
        stats.put("onlineDevices", onlineDevices);
        stats.put("apiKeyName", apiKey != null ? apiKey.getName() : null);
        stats.put("permissions", apiKey != null ? apiKey.getPermissions() : null);
        return Result.success(stats);
    }

    private Long currentUserId() {
        return SecurityUtils.requireUserId();
    }

    /**
     * 按 deviceKey 取设备并校验归属，防止 API Key 跨账号读取或操作他人设备。
     */
    private Device requireOwnedDevice(String deviceKey, Long userId) {
        Device device = deviceService.getDeviceByKey(deviceKey);
        if (!device.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
        return device;
    }

    private Map<String, Object> toDeviceMap(Device device) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", device.getId());
        map.put("deviceKey", device.getDeviceKey());
        map.put("deviceName", device.getDeviceName());
        map.put("deviceType", device.getDeviceType());
        map.put("topic", device.getTopic());
        map.put("status", device.getStatus());
        map.put("lastSeen", device.getLastSeen());
        return map;
    }
}