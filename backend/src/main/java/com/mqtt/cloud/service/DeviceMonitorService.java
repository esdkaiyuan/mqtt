package com.mqtt.cloud.service;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备监控服务
 * <p>
 * 定时巡检超过心跳超时阈值仍未上报的设备，将其置为离线。
 * 状态历史由 {@link DeviceService#updateDeviceStatus} 统一记录，此处不重复写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMonitorService {

    private final DeviceService deviceService;

    /** 心跳超时阈值（毫秒），超时未上报的设备判定为离线 */
    @Value("${app.device.status-timeout:300000}")
    private long statusTimeoutMillis;

    @Scheduled(fixedRateString = "${app.device.status-check-interval:60000}")
    public void checkDeviceOnlineStatus() {
        int timeoutMinutes = Math.max(1, (int) Math.ceil(statusTimeoutMillis / 60000.0));
        List<Device> timeoutDevices = ((DeviceMapper) deviceService.getBaseMapper())
                .findTimeoutDevices(timeoutMinutes);

        for (Device device : timeoutDevices) {
            deviceService.updateDeviceStatus(device.getId(), DeviceStatusValue.OFFLINE);
        }

        if (!timeoutDevices.isEmpty()) {
            log.info("设备状态检查完成，发现 {} 个设备超时离线", timeoutDevices.size());
        }
    }
}