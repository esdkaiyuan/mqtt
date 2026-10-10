package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.AlertProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.AlertNotifier;
import com.mqtt.cloud.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警通知实现（T-17 设计文档 §5.1 / §8.4，T-27 设计文档 §8.1 改造）。
 * <p>
 * 对外契约不变（{@code notifyTriggered} / {@code notifyRecovered}），内部改为委托
 * {@link NotificationDispatcher}：载荷仍按 T-17 契约组装为字段映射，站内信、内置 Webhook、
 * 邮件与短信的投递由分发器统一组织。T-17 的 {@code notify-enabled} / {@code recover-notify-enabled}
 * 开关仍在此处把关（关闭即返回，保持既有语义）。
 * 全部异常只记 WARN，绝不冒泡到评估链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifierImpl implements AlertNotifier {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NotificationDispatcher notificationDispatcher;
    private final AlertProperties properties;

    @Override
    public void notifyTriggered(AlertRecord record, Device device) {
        if (!properties.isNotifyEnabled()) {
            return;
        }
        if (record == null) {
            return;
        }
        if (device == null) {
            log.warn("告警触发通知跳过：设备已不存在 alertId={}", record.getId());
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertId", record.getId());
            payload.put("ruleId", record.getRuleId());
            payload.put("ruleName", record.getRuleName());
            payload.put("sourceType", record.getSourceType());
            payload.put("severity", record.getSeverity());
            payload.put("identifier", record.getIdentifier());
            payload.put("title", record.getTitle());
            payload.put("triggerValue", record.getTriggerValue());
            payload.put("triggerCount", record.getTriggerCount());
            payload.put("firstTriggeredAt", format(record.getFirstTriggeredAt()));
            payload.put("lastTriggeredAt", format(record.getLastTriggeredAt()));
            dispatch(AlertConstants.EVENT_ALERT_TRIGGERED, record, device, payload);
        } catch (Exception e) {
            log.warn("告警触发通知失败，忽略: alertId={}", record.getId(), e);
        }
    }

    @Override
    public void notifyRecovered(AlertRecord record, Device device) {
        if (!properties.isRecoverNotifyEnabled()) {
            return;
        }
        if (record == null) {
            return;
        }
        if (device == null) {
            log.warn("告警恢复通知跳过：设备已不存在 alertId={}", record.getId());
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertId", record.getId());
            payload.put("ruleId", record.getRuleId());
            payload.put("ruleName", record.getRuleName());
            payload.put("sourceType", record.getSourceType());
            payload.put("severity", record.getSeverity());
            payload.put("title", record.getTitle());
            payload.put("triggerCount", record.getTriggerCount());
            payload.put("recoveredAt", format(record.getRecoveredAt()));
            dispatch(AlertConstants.EVENT_ALERT_RECOVERED, record, device, payload);
        } catch (Exception e) {
            log.warn("告警恢复通知失败，忽略: alertId={}", record.getId(), e);
        }
    }

    private void dispatch(String eventType, AlertRecord record, Device device, Map<String, Object> payload) {
        notificationDispatcher.dispatch(record.getUserId(), eventType, device, record, payload);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}