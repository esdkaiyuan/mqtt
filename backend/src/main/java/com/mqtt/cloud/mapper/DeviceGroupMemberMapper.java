package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceGroupMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备分组成员共享 Mapper（T-48）。
 */
@Mapper
public interface DeviceGroupMemberMapper extends BaseMapper<DeviceGroupMember> {
}
