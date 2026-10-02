package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 设备Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    Device findByDeviceKey(@Param("deviceKey") String deviceKey);

    List<Device> findByDeviceKeys(@Param("keys") Collection<String> keys);

    /** 批量更新运行态：只写 status 与 last_seen，供摄取管线按批聚合后一次提交。 */
    int updateStatusBatch(@Param("list") List<Device> devices);

    /**
     * 时间戳守卫的单设备状态更新：仅当现有 {@code last_seen} 不晚于本次事件时间才落库。
     * <p>
     * 返回 0 表示该事件已过期（乱序到达的旧事件），调用方应丢弃、不写状态历史。
     */
    int updateStatusGuarded(@Param("deviceId") Long deviceId,
                            @Param("status") String status,
                            @Param("eventTime") LocalDateTime eventTime);

    List<Device> findByOwnerId(@Param("ownerId") Long ownerId);

    List<Device> findOnlineDevices(@Param("ownerId") Long ownerId);

    List<Device> findTimeoutDevices(@Param("timeoutMinutes") int timeoutMinutes);

    /**
     * 离线告警巡检：取作用域内 {@code status='OFFLINE'} 且离线时长已达阈值的设备（T-17 设计文档 §8.3）。
     *
     * @param userId         可空，非空时限定归属用户
     * @param deviceId       可空，非空时限定单台设备（规则作用域）
     * @param offlineSeconds 离线持续阈值（秒），0 表示状态一变 OFFLINE 即满足
     * @param limit          单轮上限
     */
    List<Device> findOfflineDevicesForAlert(@Param("userId") Long userId,
                                            @Param("deviceId") Long deviceId,
                                            @Param("offlineSeconds") int offlineSeconds,
                                            @Param("limit") int limit);

    IPage<Device> pageQuery(Page<Device> page, @Param("dto") DeviceQueryDTO dto, @Param("ownerId") Long ownerId);
}
