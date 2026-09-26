package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备状态历史Mapper
 */
@Mapper
public interface DeviceStatusHistoryMapper extends BaseMapper<DeviceStatus> {

    List<DeviceStatus> findByDeviceId(@Param("deviceId") Long deviceId);

    List<DeviceStatus> findByDeviceIdAndTimeRange(
            @Param("deviceId") Long deviceId,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);
}
