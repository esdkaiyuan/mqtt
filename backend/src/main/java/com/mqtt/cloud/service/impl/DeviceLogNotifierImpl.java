package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.DeviceLogNotifier;
import com.mqtt.cloud.service.RealtimeStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备日志实时通知实现（T-41）。
 * <p>
 * 事件名固定 {@code device-log}，载荷只带设备标识与时间戳；前端据此刷新当前时间线。
 * 发送失败只记 DEBUG：通知是旁路，日志本身已落库，不能因为 SSE 订阅者掉线影响写入链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceLogNotifierImpl implements DeviceLogNotifier {

    public static final String EVENT_DEVICE_LOG = "device-log";

    private final RealtimeStreamService realtimeStreamService;

    @Override
    public void notify(Device device) {
        if (device == null || device.getId() == null || device.getDeviceKey() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deviceId", device.getId());
            payload.put("deviceKey", device.getDeviceKey());
            payload.put("occurredAt", System.currentTimeMillis());
            realtimeStreamService.publishEvent(device.getOwnerId(), EVENT_DEVICE_LOG, payload);
        } catch (Exception e) {
            log.debug("设备日志实时通知失败（忽略）: deviceKey={}, error={}",
                    device.getDeviceKey(), e.getMessage());
        }
    }
}
