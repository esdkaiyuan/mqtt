package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.request.UpdateDeviceDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.entity.Device;

import java.util.List;

/**
 * 设备服务接口
 */
public interface DeviceService extends IService<Device> {

    /**
     * 创建设备并签发一机一密凭据。
     *
     * @return 含一次性明文密钥的创建结果，明文仅此一次返回
     */
    DeviceCreatedDTO createDevice(Long userId, CreateDeviceDTO dto);

    Device updateDevice(Long deviceId, UpdateDeviceDTO dto);

    void deleteDevice(Long deviceId);

    IPage<Device> getDevices(Long userId, DeviceQueryDTO dto);

    Device getDeviceById(Long deviceId);

    Device getDeviceByKey(String deviceKey);

    List<Device> getOnlineDevices();

    List<Device> getOnlineDevices(Long ownerId);

    void updateDeviceStatus(Long deviceId, String status);

    void updateDeviceStatusByKey(String deviceKey, String status);
}
