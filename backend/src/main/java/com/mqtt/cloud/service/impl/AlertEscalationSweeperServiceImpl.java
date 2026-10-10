package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.AlertEscalationPolicy;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.AlertEscalationService;
import com.mqtt.cloud.service.AlertEscalationSweeperService;
import com.mqtt.cloud.service.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 告警升级巡检实现（T-27 设计文档 §5.1 / §8.3）。
 * <p>
 * 升级动作以 {@code WHERE id = ? AND escalation_level = ?} 条件更新落地，受影响行数为 0 即视为
 * 「已被并发升级」，直接跳过 —— 多副本下同一级只升级一次。策略、候选查询与单条升级逐层 try-catch，
 * 单条失败只记 WARN，不影响同轮其余策略与告警。
 * <p>
 * 达到 {@code max-escalation-level} 的记录只刷新 {@code last_escalated_at} 不再通知，
 * 避免每轮巡检重复轰炸。
 */
@Slf4j
@Service
public class AlertEscalationSweeperServiceImpl implements AlertEscalationSweeperService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AlertEscalationService escalationService;
    private final AlertRecordMapper alertRecordMapper;
    private final DeviceMapper deviceMapper;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    public AlertEscalationSweeperServiceImpl(AlertEscalationService escalationService,
                                             AlertRecordMapper alertRecordMapper,
                                             DeviceMapper deviceMapper,
                                             NotificationDispatcher notificationDispatcher,
                                             NotificationProperties properties,
                                             ObjectMapper objectMapper) {
        this.escalationService = escalationService;
        this.alertRecordMapper = alertRecordMapper;
        this.deviceMapper = deviceMapper;
        this.notificationDispatcher = notificationDispatcher;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sweep() {
        List<AlertEscalationPolicy> policies;
        try {
            policies = escalationService.listEnabled(properties.getEscalation().getBatchSize());
        } catch (Exception e) {
            log.warn("升级策略取集失败，跳过本轮", e);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AlertEscalationPolicy policy : policies) {
            try {
                sweepPolicy(policy, now);
            } catch (Exception e) {
                log.warn("升级策略处理失败，跳过: policyId={}", policy.getId(), e);
            }
        }
    }

    private void sweepPolicy(AlertEscalationPolicy policy, LocalDateTime now) {
        int afterSeconds = policy.getAfterSeconds() == null ? 0 : policy.getAfterSeconds();
        LocalDateTime threshold = now.minusSeconds(afterSeconds);
        List<AlertRecord> candidates;
        try {
            candidates = alertRecordMapper.selectEscalationCandidates(policy.getUserId(), policy.getSeverity(),
                    threshold, properties.getEscalation().getBatchSize());
        } catch (Exception e) {
            log.warn("升级候选取集失败，跳过: policyId={}", policy.getId(), e);
            return;
        }
        for (AlertRecord record : candidates) {
            try {
                escalateOne(record, policy, now);
            } catch (Exception e) {
                log.warn("单条告警升级失败，跳过: alertId={}", record.getId(), e);
            }
        }
    }

    private void escalateOne(AlertRecord record, AlertEscalationPolicy policy, LocalDateTime now) {
        int current = record.getEscalationLevel() == null ? 0 : record.getEscalationLevel();
        int maxLevel = properties.getMaxEscalationLevel();
        if (current >= maxLevel) {
            // 达上限：只刷新锚点，不再升级通知，避免每轮重复轰炸
            alertRecordMapper.escalate(record.getId(), current, current, null, now);
            return;
        }
        int newLevel = current + 1;
        int rows = alertRecordMapper.escalate(record.getId(), current, newLevel, policy.getAssigneeId(), now);
        if (rows == 0) {
            // 条件更新未命中：已被并发升级或状态已变，跳过
            return;
        }
        record.setEscalationLevel(newLevel);
        record.setLastEscalatedAt(now);
        if (policy.getAssigneeId() != null) {
            record.setAssigneeId(policy.getAssigneeId());
        }
        dispatchEscalation(record, policy, now);
    }

    private void dispatchEscalation(AlertRecord record, AlertEscalationPolicy policy, LocalDateTime now) {
        Device device = record.getDeviceId() == null ? null : deviceMapper.selectById(record.getDeviceId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", record.getId());
        payload.put("ruleId", record.getRuleId());
        payload.put("ruleName", record.getRuleName());
        payload.put("sourceType", record.getSourceType());
        payload.put("severity", record.getSeverity());
        payload.put("title", record.getTitle());
        payload.put("escalationLevel", record.getEscalationLevel());
        payload.put("assigneeId", record.getAssigneeId());
        payload.put("escalatedAt", TIME_FORMATTER.format(now));
        notificationDispatcher.dispatch(record.getUserId(), AlertConstants.EVENT_ALERT_ESCALATED,
                device, record, payload, parseChannelIds(policy.getChannelIds()));
    }

    /** 解析策略的 {@code channel_ids}；为空或解析失败按「不限渠道」处理，由事件订阅匹配兜底。 */
    private Set<Long> parseChannelIds(String channelIdsJson) {
        if (channelIdsJson == null || channelIdsJson.isBlank()) {
            return null;
        }
        try {
            List<Long> ids = objectMapper.readValue(channelIdsJson, new TypeReference<>() {
            });
            return ids == null || ids.isEmpty() ? null : new LinkedHashSet<>(ids);
        } catch (Exception e) {
            log.warn("升级策略渠道列表解析失败，按不限渠道处理: channelIds={}", channelIdsJson, e);
            return null;
        }
    }
}