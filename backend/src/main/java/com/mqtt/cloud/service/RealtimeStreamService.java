package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.Device;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 设备实时数据流服务。
 * <p>
 * 以 SSE 方式向控制台推送设备上行消息，并按“设备归属 + 管理员”规则做服务端过滤，
 * 取代迁移期前端直连 Broker 的方式。
 */
public interface RealtimeStreamService {

    /**
     * 建立订阅。同一用户重复订阅会替换旧连接。
     *
     * @param userId 订阅用户 ID
     * @param admin  是否管理员（管理员可接收全部设备数据）
     */
    SseEmitter subscribe(Long userId, boolean admin);

    /**
     * 推送一条设备消息给有权限的订阅者。
     *
     * @param device  消息来源设备
     * @param topic   MQTT Topic
     * @param payload 消息体
     */
    void publish(Device device, String topic, String payload);
}