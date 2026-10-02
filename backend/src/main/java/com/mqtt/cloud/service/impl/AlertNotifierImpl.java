package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.AlertProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.AlertNotifier;
import com.mqtt.cloud.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警通知实现（T-17 设计文档 §5.1 / §8.4）。
 * <p>
 * 载荷按设计文档 §5.1 契约组装为 JSON 文本后，交给 {@link WebhookDispatcher} 的信封
 * （{@code event/deviceKey/deviceName/deviceType/topic/status/payload/timestamp}）投递；
 * 时间统一格式化为 ISO-8601 本地时间字符串，避免依赖 Jackson 的日期配置。
 * 全部异常只记 WARN，绝不冒泡到评估链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifierImpl implements AlertNotifier {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final WebhookDispatcher webhookDispatcher;
    private final ObjectMapper objectMapper;
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

    private void dispatch(String eventType, AlertRecord record, Device device, Map<String, Object> payload)
            throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        webhookDispatcher.dispatch(record.getDeviceId(), eventType, device, json);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
