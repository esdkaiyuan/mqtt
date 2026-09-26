package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mqtt.cloud.dto.request.MessageQueryDTO;
import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Message;

/**
 * 消息服务接口
 */
public interface MessageService extends IService<Message> {

    Message publishMessage(PublishMessageDTO dto, Long userId);

    IPage<Message> getMessages(MessageQueryDTO dto, Long userId);

    void saveReceivedMessage(Long deviceId, String topic, String payload, Integer qos);
}
