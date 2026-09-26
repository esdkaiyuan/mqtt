package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.SendCommandRequest;
import com.mqtt.cloud.entity.ApiKey;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mqtt.MqttClientManager;
import com.mqtt.cloud.service.ApiKeyService;
import com.mqtt.cloud.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放 API（供第三方系统通过 API Key 调用）
 * <p>
 * 认证由 {@code ApiKeyAuthFilter} 完成，本控制器只负责数据隔离与业务处理：
 * 所有查询与操作都被限制在当前 API Key 所属用户的名下设备范围内。
 */
@Slf4j
@RestController
@RequestMapping("/external/v1")
@RequiredArgsConstructor
public class ExternalApiController {

    private final ApiKeyService apiKeyService;
    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    private final MqttClientManager mqttClientManager;

    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices(@RequestParam(required = false) String deviceKey) {
        Long userId = currentUserId();
        if (deviceKey != null && !deviceKey.isBlank()) {
            return Result.success(List.of(toDeviceMap(requireOwnedDevice(deviceKey, userId))));
        }

        List<Map<String, Object>> devices = deviceMapper.findByOwnerId(userId).stream()
                .map(this::toDeviceMap)
                .toList();
        return Result.success(devices);
    }

    @GetMapping("/devices/{deviceKey}")
    public Result<Map<String, Object>> getDevice(@PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());

        Map<String, Object> result = toDeviceMap(device);
        result.put("description", device.getDescription());
        result.put("metadata", device.getMetadata());
        return Result.success(result);
    }

    @GetMapping("/devices/{deviceKey}/status")
    public Result<Map<String, Object>> getDeviceStatus(@PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("deviceKey", device.getDeviceKey());
        result.put("status", device.getStatus());
        result.put("lastSeen", device.getLastSeen());
        result.put("online", "ONLINE".equals(device.getStatus()));
        return Result.success(result);
    }

    @PostMapping("/devices/{deviceKey}/command")
    public Result<String> sendCommand(@PathVariable String deviceKey,
                                      @Valid @RequestBody SendCommandRequest request) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        String commandTopic = resolveCommandTopic(device.getTopic());

        try {
            mqttClientManager.publish(commandTopic, request.getPayload(), request.getQos());
        } catch (MqttException e) {
            log.error("开放接口下发指令失败: deviceKey={}, topic={}", deviceKey, commandTopic, e);
            throw new BusinessException(ResultCode.MQTT_PUBLISH_FAILED);
        }

        return Result.success("指令发送成功");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        List<Device> devices = deviceMapper.findByOwnerId(principal.getUserId());
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

    /**
     * 由设备上行 Topic 推导下行指令 Topic：device/{key}/data → device/{key}/command
     */
    private String resolveCommandTopic(String deviceTopic) {
        if (deviceTopic == null || deviceTopic.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设备未配置 Topic");
        }
        int lastSlash = deviceTopic.lastIndexOf('/');
        if (lastSlash < 0) {
            return deviceTopic + "/command";
        }
        return deviceTopic.substring(0, lastSlash) + "/command";
    }
}