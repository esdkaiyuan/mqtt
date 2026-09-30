package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.request.UpdateDeviceDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.entity.Device;

import java.time.LocalDateTime;
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

    /**
     * 禁用设备：连接许可置为禁止，已连接设备将在下次认证时被拒绝，并主动失效认证缓存。
     * 已禁用时重复调用幂等成功。
     */
    void disableDevice(Long deviceId);

    /**
     * 启用设备：恢复连接许可，并主动失效认证缓存使启用立即生效。
     * 已启用时重复调用幂等成功。
     */
    void enableDevice(Long deviceId);

    IPage<Device> getDevices(Long userId, DeviceQueryDTO dto);

    Device getDeviceById(Long deviceId);

    Device getDeviceByKey(String deviceKey);

    /** 某用户名下的全部设备（不做分页，供统计与开放 API 使用）。 */
    List<Device> getDevicesByOwner(Long ownerId);

    /** 巡检用：最近心跳早于 {@code timeoutMinutes} 分钟的在线设备。 */
    List<Device> findTimeoutDevices(int timeoutMinutes);

    List<Device> getOnlineDevices();

    List<Device> getOnlineDevices(Long ownerId);

    void updateDeviceStatus(Long deviceId, String status);

    /**
     * 带事件时间的状态更新：仅当事件时间不早于库内 {@code last_seen} 时才落库，
     * 乱序到达的旧事件被丢弃（不覆盖新状态、不写状态历史）。
     */
    void updateDeviceStatus(Long deviceId, String status, LocalDateTime eventTime);

    void updateDeviceStatusByKey(String deviceKey, String status);
}
