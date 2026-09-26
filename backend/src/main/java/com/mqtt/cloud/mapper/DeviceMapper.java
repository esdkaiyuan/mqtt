package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    Device findByDeviceKey(@Param("deviceKey") String deviceKey);

    List<Device> findByOwnerId(@Param("ownerId") Long ownerId);

    List<Device> findOnlineDevices(@Param("ownerId") Long ownerId);

    List<Device> findTimeoutDevices(@Param("timeoutMinutes") int timeoutMinutes);

    IPage<Device> pageQuery(Page<Device> page, @Param("dto") DeviceQueryDTO dto, @Param("ownerId") Long ownerId);
}
