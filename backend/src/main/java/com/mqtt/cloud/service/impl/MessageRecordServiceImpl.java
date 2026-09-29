package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Message;
import com.mqtt.cloud.mapper.MessageMapper;
import com.mqtt.cloud.service.MessageRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MessageRecordServiceImpl implements MessageRecordService {

    private static final String DIRECTION_PUBLISH = "PUBLISH";

    private final MessageMapper messageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message savePublishRecord(PublishMessageDTO dto) {
        Message message = new Message();
        message.setTopic(dto.getTopic());
        message.setDirection(DIRECTION_PUBLISH);
        message.setPayload(dto.getPayload());
        message.setQos(dto.getQos());
        message.setDeviceId(dto.getDeviceId());
        message.setSentAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long id) {
        messageMapper.deleteById(id);
    }
}