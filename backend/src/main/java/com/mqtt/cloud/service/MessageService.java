package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.MessageQueryDTO;
import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Message;

import java.util.List;

/**
 * 消息服务接口
 */
public interface MessageService extends IService<Message> {

    Message publishMessage(PublishMessageDTO dto, Long userId);

    IPage<Message> getMessages(MessageQueryDTO dto, Long userId);

    /** 当前用户名下设备的最近实时消息（平台直发消息一并可见），条数由调用方裁剪后传入。 */
    List<Message> getRecentMessages(int limit, Long userId);

    void saveReceivedMessage(Long deviceId, String topic, String payload, Integer qos);
}
