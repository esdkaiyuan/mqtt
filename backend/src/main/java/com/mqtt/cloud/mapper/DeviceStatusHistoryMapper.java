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

    /** 批量写入状态变更历史，供摄取管线一次提交。 */
    int insertBatch(@Param("list") List<DeviceStatus> statuses);
}
