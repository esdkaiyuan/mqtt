package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.dto.request.MessageQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;
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

    /** 当前用户名下设备的最近消息；平台直发（device_id 为空）消息一并可见，与 /messages 列表口径一致。 */
    List<Message> findRecentMessagesByOwner(@Param("limit") int limit, @Param("ownerId") Long ownerId);

    /** 批量插入上行消息，供摄取管线攒批落库。 */
    int insertBatch(@Param("list") List<Message> messages);

    IPage<Message> pageQuery(Page<Message> page, @Param("dto") MessageQueryDTO dto, @Param("userId") Long userId);

    /** 历史查询改由 message 承载，签名与原 history_record 查询保持一致。 */
    IPage<HistoryRecord> pageHistoryQuery(Page<HistoryRecord> page,
                                          @Param("dto") HistoryQueryDTO dto,
                                          @Param("userId") Long userId);
}
