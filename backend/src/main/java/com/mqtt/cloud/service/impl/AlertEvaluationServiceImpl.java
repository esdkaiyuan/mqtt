package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.AlertProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.AlertRuleMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.AlertEvaluationService;
import com.mqtt.cloud.service.AlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警评估实现（T-17 设计文档 §8.2 / §8.5）。
 * <p>
 * 三类来源共用 {@link #raise} 的状态流转：无活动告警则新建并发通知；有活动告警则按抑制窗口
 * 判定「去重（计数累加、不重复通知）」或「超窗再次通知」；{@link #recover} 置终态并发恢复通知。
 * 逐条隔离：任何评估 / 落库 / 通知异常只记 WARN，绝不冒泡到解析或巡检链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationServiceImpl implements AlertEvaluationService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final DeviceMapper deviceMapper;
    private final AlertNotifier alertNotifier;
    private final AlertProperties properties;

    @Override
    public void onProperties(Long deviceId, Long productId, List<PropertySample> samples) {
        if (!properties.isEnabled() || deviceId == null || samples == null || samples.isEmpty()) {
            return;
        }
        try {
            Device device = loadDevice(deviceId);
            if (device == null) {
                return;
            }
            List<AlertRule> rules = alertRuleMapper.selectEnabledForDevice(
                    device.getOwnerId(), deviceId, AlertConstants.SOURCE_THRESHOLD);
            for (AlertRule rule : rules) {
                for (PropertySample sample : samples) {
                    if (!matchesIdentifier(rule.getIdentifier(), sample.identifier())) {
                        continue;
                    }
                    evaluateThreshold(rule, device, sample);
                }
            }
        } catch (Exception e) {
            log.warn("属性阈值告警评估异常，跳过: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void onEvents(Long deviceId, List<EventSample> samples) {
        if (!properties.isEnabled() || deviceId == null || samples == null || samples.isEmpty()) {
            return;
        }
        try {
            Device device = loadDevice(deviceId);
            if (device == null) {
                return;
            }
            List<AlertRule> rules = alertRuleMapper.selectEnabledForDevice(
                    device.getOwnerId(), deviceId, AlertConstants.SOURCE_EVENT);
            for (AlertRule rule : rules) {
                for (EventSample sample : samples) {
                    if (!matchesIdentifier(rule.getIdentifier(), sample.identifier())) {
                        continue;
                    }
                    if (rule.getEventType() != null && !rule.getEventType().equals(sample.eventType())) {
                        continue;
                    }
                    // 事件类无自动恢复：命中即触发，仅支持人工关闭
                    raise(rule, device, eventTitle(rule), sample.outputData());
                }
            }
        } catch (Exception e) {
            log.warn("事件告警评估异常，跳过: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void evaluateOffline(AlertRule rule, Device device) {
        if (!properties.isEnabled() || rule == null || device == null) {
            return;
        }
        try {
            raise(rule, device, offlineTitle(rule), null);
        } catch (Exception e) {
            log.warn("离线告警评估异常，跳过: ruleId={}, deviceId={}", rule.getId(), device.getId(), e);
        }
    }

    @Override
    public void recover(AlertRecord record, Device device) {
        if (record == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            int rows = alertRecordMapper.markRecovered(record.getId(), now);
            if (rows == 0) {
                // 已被并发置恢复，静默返回
                return;
            }
            record.setStatus(AlertConstants.STATUS_RECOVERED);
            record.setRecoveredAt(now);
            alertNotifier.notifyRecovered(record, device);
        } catch (Exception e) {
            log.warn("告警恢复处理异常，跳过: alertId={}", record.getId(), e);
        }
    }

    /** 阈值样本比较：命中则触发，未命中则对活动告警做回落恢复。 */
    private void evaluateThreshold(AlertRule rule, Device device, PropertySample sample) {
        Boolean matched = compare(sample.valueText(), rule.getOperator(), rule.getThresholdValue(), rule.getId());
        if (matched == null) {
            return;
        }
        if (matched) {
            raise(rule, device, thresholdTitle(rule), sample.valueText());
            return;
        }
        AlertRecord open = alertRecordMapper.selectOpenByRuleAndDevice(rule.getId(), device.getId());
        if (open != null) {
            recover(open, device);
        }
    }

    /**
     * 评估核心四步（设计文档 §8.2）：查活动告警 → 新建 / 去重 / 超窗再通知。
     * 抑制窗口内重复触发只累加计数、刷新最近触发时间与触发值，不落新行、不重复通知。
     */
    private void raise(AlertRule rule, Device device, String title, String triggerValue) {
        LocalDateTime now = LocalDateTime.now();
        AlertRecord open = alertRecordMapper.selectOpenByRuleAndDevice(rule.getId(), device.getId());
        if (open == null) {
            AlertRecord record = new AlertRecord();
            record.setUserId(rule.getUserId());
            record.setDeviceId(device.getId());
            record.setDeviceKey(device.getDeviceKey());
            record.setRuleId(rule.getId());
            record.setRuleName(rule.getName());
            record.setSourceType(rule.getSourceType());
            record.setSeverity(rule.getSeverity());
            record.setIdentifier(rule.getIdentifier());
            record.setTitle(title);
            record.setTriggerValue(triggerValue);
            record.setStatus(AlertConstants.STATUS_TRIGGERED);
            record.setTriggerCount(1);
            record.setFirstTriggeredAt(now);
            record.setLastTriggeredAt(now);
            record.setNotifiedAt(now);
            alertRecordMapper.insert(record);
            alertNotifier.notifyTriggered(record, device);
            return;
        }

        int triggerCount = (open.getTriggerCount() == null ? 0 : open.getTriggerCount()) + 1;
        long windowSeconds = resolveWindowSeconds(rule);
        boolean inWindow = open.getLastTriggeredAt() != null
                && Duration.between(open.getLastTriggeredAt(), now).getSeconds() <= windowSeconds;
        // updateTrigger 会无条件覆写 notified_at，窗口内必须回传原值，避免误刷新通知时间
        LocalDateTime notifiedAt = inWindow ? open.getNotifiedAt() : now;
        int rows = alertRecordMapper.updateTrigger(open.getId(), triggerCount, now, triggerValue, notifiedAt);
        if (rows == 0) {
            // 记录已被并发置恢复，本轮不再通知
            return;
        }
        if (!inWindow) {
            open.setTriggerCount(triggerCount);
            open.setLastTriggeredAt(now);
            open.setTriggerValue(triggerValue);
            open.setNotifiedAt(notifiedAt);
            alertNotifier.notifyTriggered(open, device);
        }
    }

    /**
     * 阈值比较（设计文档 §7.2）：数值比较符两侧均为十进制数时按 {@code BigDecimal.compareTo}；
     * 任一侧不可解析则返回 {@code null} 表示跳过（不触发）；{@code EQ}/{@code NE} 按归一化文本相等。
     */
    private Boolean compare(String value, String operator, String threshold, Long ruleId) {
        if (value == null || operator == null || threshold == null) {
            return null;
        }
        if (AlertConstants.NUMERIC_OPERATORS.contains(operator)) {
            BigDecimal left;
            BigDecimal right;
            try {
                left = new BigDecimal(value.trim());
                right = new BigDecimal(threshold.trim());
            } catch (NumberFormatException e) {
                log.warn("阈值比较跳过：数值不可解析 ruleId={}, value={}, threshold={}", ruleId, value, threshold);
                return null;
            }
            int cmp = left.compareTo(right);
            switch (operator) {
                case AlertConstants.OPERATOR_GT:
                    return cmp > 0;
                case AlertConstants.OPERATOR_GTE:
                    return cmp >= 0;
                case AlertConstants.OPERATOR_LT:
                    return cmp < 0;
                case AlertConstants.OPERATOR_LTE:
                    return cmp <= 0;
                default:
                    return null;
            }
        }
        boolean equal = value.trim().equals(threshold.trim());
        if (AlertConstants.OPERATOR_EQ.equals(operator)) {
            return equal;
        }
        if (AlertConstants.OPERATOR_NE.equals(operator)) {
            return !equal;
        }
        return null;
    }

    private Device loadDevice(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || device.getOwnerId() == null) {
            return null;
        }
        return device;
    }

    private long resolveWindowSeconds(AlertRule rule) {
        Integer own = rule.getSuppressWindowSeconds();
        return (own != null && own > 0) ? own : properties.getSuppressWindowSeconds();
    }

    private static boolean matchesIdentifier(String ruleIdentifier, String sampleIdentifier) {
        return ruleIdentifier != null && ruleIdentifier.equals(sampleIdentifier);
    }

    private static String thresholdTitle(AlertRule rule) {
        return rule.getIdentifier() + " " + operatorLabel(rule.getOperator()) + " " + rule.getThresholdValue();
    }

    private static String offlineTitle(AlertRule rule) {
        Integer seconds = rule.getOfflineSeconds();
        return (seconds != null && seconds > 0) ? "设备离线超过 " + seconds + " 秒" : "设备离线";
    }

    private static String eventTitle(AlertRule rule) {
        return "事件 " + rule.getIdentifier();
    }

    private static String operatorLabel(String operator) {
        if (operator == null) {
            return "满足";
        }
        switch (operator) {
            case AlertConstants.OPERATOR_GT:
                return "超过";
            case AlertConstants.OPERATOR_GTE:
                return "不低于";
            case AlertConstants.OPERATOR_LT:
                return "低于";
            case AlertConstants.OPERATOR_LTE:
                return "不高于";
            case AlertConstants.OPERATOR_EQ:
                return "等于";
            case AlertConstants.OPERATOR_NE:
                return "不等于";
            default:
                return "满足";
        }
    }
}