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
import com.mqtt.cloud.service.AlertSweeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线巡检实现（T-17 设计文档 §8.3）。
 * <p>
 * 每轮：① 取启用中的 {@code OFFLINE} 规则（按 {@code sweepBatchSize} 分批）；
 * ② 对每条规则查作用域内离线时长已达阈值的设备，逐条交给评估服务触发（去重 / 新建）；
 * ③ 对每条规则的活动告警，若对应设备已重新上线（或规则已逻辑删除）则置 {@code RECOVERED}。
 * <p>
 * 恢复判定用「当前在线设备集」而非「离线设备集」的差集，避免离线查询 {@code LIMIT} 截断时
 * 把仍在离线的设备误判为已恢复；在线集按用户在一次巡检内缓存，避免逐规则重复查询。
 * 单条规则异常只记 WARN，不影响同轮其余规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertSweeperServiceImpl implements AlertSweeperService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final DeviceMapper deviceMapper;
    private final AlertEvaluationService alertEvaluationService;
    private final AlertProperties properties;

    @Override
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        int batchSize = properties.getSweepBatchSize();
        Map<Long, Map<Long, Device>> onlineCache = new HashMap<>();
        try {
            List<AlertRule> rules = alertRuleMapper.selectEnabledBySource(
                    AlertConstants.SOURCE_OFFLINE, batchSize);
            for (AlertRule rule : rules) {
                try {
                    sweepRule(rule, batchSize, onlineCache);
                } catch (Exception e) {
                    log.warn("离线告警巡检单条规则失败，跳过: ruleId={}", rule.getId(), e);
                }
            }
        } catch (Exception e) {
            log.warn("离线告警巡检取规则失败", e);
        }
        recoverDeletedRules(batchSize);
    }

    private void sweepRule(AlertRule rule, int batchSize, Map<Long, Map<Long, Device>> onlineCache) {
        int offlineSeconds = rule.getOfflineSeconds() == null ? 0 : rule.getOfflineSeconds();
        List<Device> offlineDevices = deviceMapper.findOfflineDevicesForAlert(
                rule.getUserId(), rule.getDeviceId(), offlineSeconds, batchSize);
        for (Device device : offlineDevices) {
            alertEvaluationService.evaluateOffline(rule, device);
        }
        recoverOnline(rule, onlineCache);
    }

    /** 恢复分支：作用域内已上线的设备，其该规则的活动告警一并置恢复。 */
    private void recoverOnline(AlertRule rule, Map<Long, Map<Long, Device>> onlineCache) {
        Map<Long, Device> online = onlineCache.computeIfAbsent(rule.getUserId(), this::loadOnlineDevices);
        List<Long> scopeOnlineIds;
        if (rule.getDeviceId() != null) {
            scopeOnlineIds = online.containsKey(rule.getDeviceId())
                    ? List.of(rule.getDeviceId()) : List.of();
        } else {
            scopeOnlineIds = new ArrayList<>(online.keySet());
        }
        if (scopeOnlineIds.isEmpty()) {
            return;
        }
        List<AlertRecord> openRecords = alertRecordMapper.selectOpenByRuleAndDevices(rule.getId(), scopeOnlineIds);
        for (AlertRecord record : openRecords) {
            alertEvaluationService.recover(record, online.get(record.getDeviceId()));
        }
    }

    private Map<Long, Device> loadOnlineDevices(Long userId) {
        Map<Long, Device> result = new HashMap<>();
        for (Device device : deviceMapper.findOnlineDevices(userId)) {
            result.put(device.getId(), device);
        }
        return result;
    }

    /**
     * 规则删除兜底：规则逻辑删除后不再被 {@code selectEnabledBySource} 命中，其活动告警不会进入
     * 按规则恢复判定，需单独收敛，避免永久停留活动态（设计文档 §13⑥）。
     */
    private void recoverDeletedRules(int batchSize) {
        try {
            List<AlertRecord> records = alertRecordMapper.selectOpenByDeletedRule(batchSize);
            for (AlertRecord record : records) {
                alertEvaluationService.recover(record, deviceMapper.selectById(record.getDeviceId()));
            }
        } catch (Exception e) {
            log.warn("离线告警巡检规则删除兜底失败", e);
        }
    }
}