package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 历史记录Mapper
 */
@Mapper
public interface HistoryRecordMapper extends BaseMapper<HistoryRecord> {

    List<HistoryRecord> findByDeviceIdAndTimeRange(
            @Param("deviceId") Long deviceId,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    IPage<HistoryRecord> pageQuery(Page<HistoryRecord> page, @Param("dto") HistoryQueryDTO dto, @Param("userId") Long userId);
}
