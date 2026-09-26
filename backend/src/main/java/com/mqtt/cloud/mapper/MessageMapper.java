package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.MessageQueryDTO;
import com.mqtt.cloud.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息Mapper
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    List<Message> findByTopic(@Param("topic") String topic);

    List<Message> findByDeviceId(@Param("deviceId") Long deviceId);

    List<Message> findRecentMessages(@Param("limit") int limit);

    IPage<Message> pageQuery(Page<Message> page, @Param("dto") MessageQueryDTO dto, @Param("userId") Long userId);
}
