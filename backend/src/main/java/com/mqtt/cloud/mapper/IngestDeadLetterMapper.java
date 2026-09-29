package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.IngestDeadLetter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 上行摄取死信Mapper
 */
@Mapper
public interface IngestDeadLetterMapper extends BaseMapper<IngestDeadLetter> {

    /** 批量写入死信，供溢出与落库失败路径一次性留痕。 */
    int insertBatch(@Param("list") List<IngestDeadLetter> letters);
}