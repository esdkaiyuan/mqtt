package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mqtt.cloud.entity.DeviceStatus;

import java.util.List;

/**
 * 设备状态历史服务接口
 */
public interface DeviceStatusHistoryService extends IService<DeviceStatus> {

    void recordStatusChange(Long deviceId, String newStatus);

    List<DeviceStatus> getHistoryByDeviceId(Long deviceId);
}
