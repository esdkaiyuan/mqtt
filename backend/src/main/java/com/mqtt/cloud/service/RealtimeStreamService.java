package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.Device;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;

/**
 * 设备实时数据流服务。
 * <p>
 * 以 SSE 方式向控制台推送设备上行消息，并按“设备归属 + 管理员”规则做服务端过滤，
 * 取代迁移期前端直连 Broker 的方式。
 * <p>
 * T-36 起扩展为**多主题**：原先只有 {@code device-data} 一个硬编码事件名，
 * 扩展出 {@code device-log}（设备日志增量）与 {@code property-trend}（属性上报增量），
 * 让原本只能轮询的日志与趋势页也能实时追加。
 * <p>
 * 订阅方可按主题过滤（见 {@link #subscribe(Long, boolean, java.util.Set)}）：
 * 空集合表示订阅全部主题，保持与改造前单主题调用的等价语义。
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
     * 按主题建立订阅（T-36）。{@code topics} 为空集合表示订阅全部主题。
     *
     * @param userId 订阅用户 ID
     * @param admin  是否管理员
     * @param topics 订阅的事件名集合；空 = 全订阅
     */
    SseEmitter subscribe(Long userId, boolean admin, Set<String> topics);

    /**
     * 向「管理员 + 指定归属人」的订阅者推送一条自定义主题事件（T-36）。
     * <p>
     * 与 {@link #publishLocal} 的区别：不绑定设备与 Topic，直接给定事件名与载荷，
     * 供属性历史写入点等「没有单一设备上下文」的场景使用。
     * <p>
     * 全程异步投递，投递异常不冒泡到调用方线程 —— 调用点往往在摄取主链路上，
     * 一个慢客户端不得影响消息处理。
     *
     * @param ownerId   归属用户 ID，为 null 时仅管理员可见
     * @param eventName 事件名（对应 SSE 的 event 名）
     * @param payload   载荷（按键值对展开）
     */
    void publishEvent(Long ownerId, String eventName, Map<String, Object> payload);

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