package com.mqtt.cloud.event;

import java.time.LocalDateTime;

/**
 * 设备上下线状态真正变化事件（T-43）。
 * <p>
 * 仅在 ONLINE / OFFLINE 实际切换且落库成功后发布：状态未变化、乱序旧事件被 SQL 守卫丢弃时不发布，
 * 避免场景重复触发。监听方在事务提交后消费，回滚不会产生副作用。
 */
public record DeviceStatusChangedEvent(Long deviceId, String status, LocalDateTime occurredAt) {
}
