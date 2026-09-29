package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Message;

/**
 * 发布消息的独立事务边界。
 * <p>
 * 把「消息落库」与「MQTT 网络发布」拆成两步：落库走本服务的事务方法，发布在事务外执行。
 * MQTT 发布不可回滚，若与 DB 事务混在一起会产生「伪事务」——DB 回滚而消息已发出，
 * 或网络往返拉长事务持有时间。
 */
public interface MessageRecordService {

    /** 落库一条 PUBLISH 记录（独立短事务），返回已带主键的实体。 */
    Message savePublishRecord(PublishMessageDTO dto);

    /** 发布失败时补偿删除刚落的记录（独立短事务）。 */
    void deleteRecord(Long id);
}