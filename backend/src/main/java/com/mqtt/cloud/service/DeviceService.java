package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.request.CreateSubDeviceDTO;
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

    /**
     * 在指定网关下创建子设备并挂载（T-26 设计文档 §8.1）。
     * <p>
     * 子设备的产品与归属取自网关，请求体不携带 {@code productId}。先按网关产品落一条普通设备行，
     * 再经 {@link DeviceTopologyService#attach(Long, Long)} 完成父设备、同产品、层级与数量校验并挂载，
     * 最后在同一事务内签发一机一密凭据；两个入口共用同一套校验与同一写路径，任一步失败整体回滚。
     *
     * @return 含一次性明文密钥的创建结果，明文仅此一次返回
     */
    DeviceCreatedDTO createSubDevice(Long gatewayId, CreateSubDeviceDTO dto);

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

    /**
     * 按筛选条件取设备列表（不分页，供 CSV 导出台账使用）。
     * <p>
     * {@code limit} 由调用方收敛到导出上限；返回值最多 {@code limit} 条，
     * 是否截断由调用方按「返回值条数 >= limit」判断。
     */
    java.util.List<Device> listForExport(DeviceQueryDTO dto, int limit);

    /**
     * 按已解析的分组范围分页设备（T-48 共享分组用）。
     * <p>
     * {@code dto.ownerId} 为空时不追加 owner 条件，仅依赖调用方已校验的 {@code groupIds} 限定范围。
     */
    IPage<Device> getDevicesInGroups(DeviceQueryDTO dto);

    Device getDeviceById(Long deviceId);

    Device getDeviceByKey(String deviceKey);

    /** 某用户名下的全部设备（不做分页，供统计与开放 API 使用）。 */
    List<Device> getDevicesByOwner(Long ownerId);

    /** 某用户名下最多返回 limit 台设备，供影子摘要等有上限的聚合接口使用。 */
    List<Device> getDevicesByOwner(Long ownerId, int limit);

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

    /**
     * 回写设备当前固件版本（T-54）。
     * <p>
     * 由 OTA 成功回传调用；版本为空时忽略。是否允许降级由调用方按
     * {@code app.ota.allow-downgrade} 判定，本方法只负责落库。
     */
    void updateFirmwareVersion(Long deviceId, String firmwareVersion);
}
