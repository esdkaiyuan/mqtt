package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceShadow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 设备影子 Mapper（T-16）。
 * <p>
 * 提供两个自定义写操作：{@link #insertIfAbsent}（懒创建、幂等）与
 * {@link #casUpdate}（版本号 CAS，避免多副本并发合并丢失更新）。
 * 读取统一用 {@code BaseMapper.selectOne} + LambdaQueryWrapper。
 */
@Mapper
public interface DeviceShadowMapper extends BaseMapper<DeviceShadow> {

    /**
     * 懒创建：设备首行不存在时插入空影子（{@code version = 0}），已存在则不做任何变更。
     * <p>
     * 幂等：{@code ON DUPLICATE KEY UPDATE id = id} 命中唯一键时不改变任何列。
     *
     * @return 受影响行数（插入 1 / 已存在 0），调用方不依赖其判定成功
     */
    int insertIfAbsent(@Param("deviceId") Long deviceId, @Param("now") LocalDateTime now);

    /**
     * 版本号 CAS 更新：仅当库中 {@code version} 等于 {@code expectedVersion} 时写入，
     * 成功后 {@code version = version + 1}。
     *
     * @return 受影响行数；0 表示并发变更或影子行不存在，调用方需重读重算
     */
    int casUpdate(@Param("deviceId") Long deviceId,
                  @Param("desired") String desired,
                  @Param("reported") String reported,
                  @Param("delta") String delta,
                  @Param("expectedVersion") Long expectedVersion,
                  @Param("updatedAt") LocalDateTime updatedAt);
}
