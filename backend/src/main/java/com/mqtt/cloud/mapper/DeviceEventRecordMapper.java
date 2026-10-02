package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceEventRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备事件记录 Mapper。
 */
@Mapper
public interface DeviceEventRecordMapper extends BaseMapper<DeviceEventRecord> {

    /** 事件追加写入，由解析链路按批一次提交。 */
    int insertBatch(@Param("list") List<DeviceEventRecord> records);
}