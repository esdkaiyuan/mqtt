package com.mqtt.cloud.event;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备影子实际变更事件（T-45）。
 * <p>
 * 仅在版本 CAS 成功且目标侧确有值变化时发布；{@code changes} 只包含发生变化的属性。
 * {@code source} 为命令来源（{@code CONSOLE} / {@code OPEN_API} / {@code SCENE} / {@code RULE} 等），
 * 场景评估据此过滤内部自动化产生的 desired 变更，避免自触发循环；reported 来源为 {@code INGEST}。
 */
public record DeviceShadowChangedEvent(Long deviceId,
                                       String side,
                                       Map<String, String> changes,
                                       long version,
                                       String source,
                                       LocalDateTime occurredAt) {
}
