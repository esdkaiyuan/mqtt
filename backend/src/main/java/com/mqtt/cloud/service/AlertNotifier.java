package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;

/**
 * 告警通知器（T-17 设计文档 §5.1 / §8.4）。
 * <p>
 * 通知出口**复用既有 Webhook**（{@code alert.triggered} / {@code alert.recovered} 两个事件类型），
 * 不新建通知通道。逐条隔离：通知异常只记 WARN，不影响评估与落库。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface AlertNotifier {

    /**
     * 触发通知：投递 {@code alert.triggered}，受 {@code app.alert.notify-enabled} 控制。
     *
     * @param record 活动告警记录（提供 id / ruleId / 计数 / 时间等载荷字段）
     * @param device 关联设备；为空表示设备已不存在，此时不发通知（仅记 WARN）
     */
    void notifyTriggered(AlertRecord record, Device device);

    /**
     * 恢复通知：投递 {@code alert.recovered}，受 {@code app.alert.recover-notify-enabled} 控制。
     *
     * @param record 已置 RECOVERED 的记录
     * @param device 关联设备；为空表示设备已不存在，此时不发通知（仅记 WARN）
     */
    void notifyRecovered(AlertRecord record, Device device);
}
