package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Message;
import com.mqtt.cloud.mqtt.MqttClientManager;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.MessageRecordService;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private MqttClientManager mqttClientManager;
    @Mock
    private MessageRecordService messageRecordService;

    private MessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageServiceImpl(deviceService, mqttClientManager, messageRecordService);
    }

    @Test
    void publishMessage_should_persist_before_publishing_to_broker() throws Exception {
        PublishMessageDTO dto = dto(null);
        Message saved = new Message();
        saved.setId(100L);
        when(messageRecordService.savePublishRecord(dto)).thenReturn(saved);

        Message result = service.publishMessage(dto, 1L);

        assertThat(result).isSameAs(saved);
        // 落库必须发生在网络发布之前：事务内不做不可回滚的 I/O
        InOrder inOrder = inOrder(messageRecordService, mqttClientManager);
        inOrder.verify(messageRecordService).savePublishRecord(dto);
        inOrder.verify(mqttClientManager).publish(dto.getTopic(), dto.getPayload(), dto.getQos());
    }

    @Test
    void publishMessage_should_compensate_record_when_broker_rejects() throws Exception {
        PublishMessageDTO dto = dto(null);
        Message saved = new Message();
        saved.setId(100L);
        when(messageRecordService.savePublishRecord(dto)).thenReturn(saved);
        doThrow(new MqttException(MqttException.REASON_CODE_BROKER_UNAVAILABLE))
                .when(mqttClientManager).publish(anyString(), anyString(), anyInt());

        assertThatThrownBy(() -> service.publishMessage(dto, 1L))
                .isInstanceOf(BusinessException.class);

        verify(messageRecordService).deleteRecord(100L);
    }

    @Test
    void publishMessage_should_reject_device_not_owned_by_caller() {
        PublishMessageDTO dto = dto(5L);
        Device device = new Device();
        device.setId(5L);
        device.setOwnerId(2L);
        when(deviceService.getDeviceById(5L)).thenReturn(device);

        assertThatThrownBy(() -> service.publishMessage(dto, 1L))
                .isInstanceOf(BusinessException.class);

        // 鉴权失败必须在落库之前短路，不产生任何消息记录
        verifyNoInteractions(messageRecordService);
    }

    private PublishMessageDTO dto(Long deviceId) {
        PublishMessageDTO dto = new PublishMessageDTO();
        dto.setTopic("device/sensor-01/data");
        dto.setPayload("{\"data\":\"x\"}");
        dto.setQos(1);
        dto.setDeviceId(deviceId);
        return dto;
    }
}