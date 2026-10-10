package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceRegBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备注册批次Mapper（T-30）。
 * 全部为 BaseMapper 内置方法 + LambdaWrapper，无需额外 XML。
 */
@Mapper
public interface DeviceRegBatchMapper extends BaseMapper<DeviceRegBatch> {
}
