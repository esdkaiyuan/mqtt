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
     * 建立订阅。同一用户可同时持有多个连接（多标签页/多端），互不踢除。
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

    /**
     * 本地扇出：按“管理员或设备归属人”过滤后，异步推送给本实例上的订阅者。
     * <p>
     * 与调用方线程（MQTT 摄取线程、Redis 订阅线程）解耦，慢客户端不会阻塞消息链路。
     * 后续引入跨副本广播时，由广播订阅者以反序列化后的字段调用本方法。
     *
     * @param deviceId  设备 ID
     * @param deviceKey 设备标识，随事件下发供前端展示
     * @param ownerId   设备归属用户 ID，无归属时传 null（仅管理员可见）
     * @param topic     MQTT Topic
     * @param payload   消息体
     */
    void publishLocal(Long deviceId, String deviceKey, Long ownerId, String topic, String payload);
}