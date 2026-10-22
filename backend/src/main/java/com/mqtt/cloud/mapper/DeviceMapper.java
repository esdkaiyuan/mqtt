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

    /** 按归属人取有限数量设备，供影子摘要等有上限的聚合接口使用。 */
    List<Device> findByOwnerIdLimited(@Param("ownerId") Long ownerId, @Param("limit") int limit);

    List<Device> findOnlineDevices(@Param("ownerId") Long ownerId);

    List<Device> findTimeoutDevices(@Param("timeoutMinutes") int timeoutMinutes);

    /**
     * 离线告警巡检：取作用域内 {@code status='OFFLINE'} 且离线时长已达阈值的设备（T-17 §8.3 / T-28 §7.3）。
     *
     * @param userId         可空，非空时限定归属用户
     * @param deviceIds      作用域设备集合；<b>空集表示不限设备</b>（{@code ALL} 档，等价于既有全量语义），
     *                       非空时按 {@code id IN (...)} 限定（{@code DEVICE} / {@code GROUP} / {@code TAG} 档）
     * @param offlineSeconds 离线持续阈值（秒），0 表示状态一变 OFFLINE 即满足
     * @param limit          单轮上限
     */
    List<Device> findOfflineDevicesForAlert(@Param("userId") Long userId,
                                            @Param("deviceIds") Collection<Long> deviceIds,
                                            @Param("offlineSeconds") int offlineSeconds,
                                            @Param("limit") int limit);

    IPage<Device> pageQuery(Page<Device> page, @Param("dto") DeviceQueryDTO dto, @Param("ownerId") Long ownerId);

    /**
     * 按主键取「存在且未删除」的设备（T-26 拓扑校验）。
     * <p>
     * {@code BaseMapper#selectById} 不过滤 {@code deleted}，而拓扑校验要求父设备未删除，
     * 故此处显式带 {@code deleted = 0}；不取密钥哈希等敏感列。
     */
    Device findActiveById(@Param("id") Long id);

    /** 按父网关分页列子设备（{@code parent_id = 网关 id} 且未删除），支持名称/类型/状态过滤。 */
    IPage<Device> findByParentId(Page<Device> page,
                                 @Param("parentId") Long parentId,
                                 @Param("dto") DeviceQueryDTO dto);

    /** 统计该设备名下的子设备数（未删除），供数量上限与降级校验使用。 */
    long countByParentId(@Param("parentId") Long parentId);
}
