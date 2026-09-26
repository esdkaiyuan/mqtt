package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.MessageQueryDTO;
import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Message;
import com.mqtt.cloud.mapper.MessageMapper;
import com.mqtt.cloud.mqtt.MqttClientManager;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消息服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final DeviceService deviceService;
    private final MqttClientManager mqttClientManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message publishMessage(PublishMessageDTO dto, Long userId) {
        if (dto.getDeviceId() != null) {
            Device device = deviceService.getDeviceById(dto.getDeviceId());
            if (!device.getOwnerId().equals(userId)) {
                throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
            }
        }

        try {
            mqttClientManager.publish(dto.getTopic(), dto.getPayload(), dto.getQos());
        } catch (MqttException e) {
            log.error("MQTT 发布失败: topic={}", dto.getTopic(), e);
            throw new BusinessException(ResultCode.MQTT_PUBLISH_FAILED);
        }

        Message message = new Message();
        message.setTopic(dto.getTopic());
        message.setDirection("PUBLISH");
        message.setPayload(dto.getPayload());
        message.setQos(dto.getQos());
        message.setDeviceId(dto.getDeviceId());
        message.setSentAt(LocalDateTime.now());
        this.save(message);

        return message;
    }

    @Override
    public IPage<Message> getMessages(MessageQueryDTO dto, Long userId) {
        Page<Message> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        return this.baseMapper.pageQuery(page, dto, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReceivedMessage(Long deviceId, String topic, String payload, Integer qos) {
        Message message = new Message();
        message.setTopic(topic);
        message.setDirection("SUBSCRIBE");
        message.setPayload(payload);
        message.setQos(qos);
        message.setDeviceId(deviceId);
        message.setSentAt(LocalDateTime.now());
        this.save(message);
    }
}