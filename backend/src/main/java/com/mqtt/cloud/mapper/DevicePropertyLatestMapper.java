package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 设备属性最新值 Mapper。
 */
@Mapper
public interface DevicePropertyLatestMapper extends BaseMapper<DevicePropertyLatest> {

    /**
     * 时间戳守卫的 upsert：仅当本次 {@code reportedAt} 不早于库中 {@code reported_at} 才覆盖。
     * <p>
     * 与 {@code DeviceMapper.updateStatusGuarded} 同一思路，防乱序旧包覆盖新值。
     * 返回受影响行数（插入 1、更新 2、值未变 0），调用方不依赖其判定成功。
     */
    int upsertIfNewer(@Param("deviceId") Long deviceId,
                      @Param("identifier") String identifier,
                      @Param("dataType") String dataType,
                      @Param("valueText") String valueText,
                      @Param("reportedAt") LocalDateTime reportedAt);
}