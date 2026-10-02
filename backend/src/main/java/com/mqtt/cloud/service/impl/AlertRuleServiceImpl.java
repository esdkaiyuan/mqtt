package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.AlertRuleRequest;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRuleMapper;
import com.mqtt.cloud.service.AlertRuleService;
import com.mqtt.cloud.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 告警规则服务实现（T-17 设计文档 §7.1）。
 * <p>
 * 校验集中在本类，保证非法配置统一抛 {@code 6208}；设备归属校验交由
 * {@link DeviceService#getDeviceById(Long)}（不存在抛 {@code 2002}），
 * 非本人设备抛 {@code 2003}。所有写入方法开启事务。
 */
@Service
public class AlertRuleServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> implements AlertRuleService {

    private final DeviceService deviceService;

    public AlertRuleServiceImpl(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRule create(Long userId, AlertRuleRequest request) {
        AlertRule rule = new AlertRule();
        rule.setUserId(userId);
        applyAndValidate(rule, request, userId);
        save(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRule update(Long userId, Long id, AlertRuleRequest request) {
        AlertRule rule = getOwned(userId, id);
        applyAndValidate(rule, request, userId);
        updateById(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        AlertRule rule = getOwned(userId, id);
        removeById(rule.getId());
    }

    @Override
    public List<AlertRule> list(Long userId, String sourceType, Boolean enabled) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getUserId, userId);
        String normalizedSource = normalizeUpper(sourceType);
        if (normalizedSource != null) {
            wrapper.eq(AlertRule::getSourceType, normalizedSource);
        }
        if (enabled != null) {
            wrapper.eq(AlertRule::getEnabled, enabled ? 1 : 0);
        }
        wrapper.orderByDesc(AlertRule::getCreatedAt).orderByDesc(AlertRule::getId);
        return list(wrapper);
    }

    @Override
    public AlertRule getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.ALERT_RULE_NOT_FOUND);
        }
        AlertRule rule = getById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.ALERT_RULE_NOT_FOUND);
        }
        if (!userId.equals(rule.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return rule;
    }

    /**
     * 校验请求并把规范化后的字段写入 {@code rule}。任何非法项抛 {@code 6208}。
     */
    private void applyAndValidate(AlertRule rule, AlertRuleRequest request, Long userId) {
        if (request == null) {
            throw invalid("请求体不能为空");
        }

        String name = trimToNull(request.getName());
        if (name == null) {
            throw invalid("规则名称不能为空");
        }
        if (name.length() > 64) {
            throw invalid("规则名称长度不能超过 64");
        }

        String sourceType = normalizeUpper(request.getSourceType());
        if (sourceType == null || !AlertConstants.SOURCES.contains(sourceType)) {
            throw invalid("来源类型必须是 THRESHOLD / OFFLINE / EVENT");
        }

        String severity = normalizeUpper(request.getSeverity());
        if (severity == null) {
            severity = AlertConstants.SEVERITY_WARNING;
        }
        if (!AlertConstants.SEVERITIES.contains(severity)) {
            throw invalid("级别必须是 INFO / WARNING / CRITICAL");
        }

        Long deviceId = request.getDeviceId();
        if (deviceId != null) {
            Device device = deviceService.getDeviceById(deviceId);
            if (!userId.equals(device.getOwnerId())) {
                throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
            }
        }

        int offlineSeconds = request.getOfflineSeconds() == null ? 0 : request.getOfflineSeconds();
        if (offlineSeconds < 0 || offlineSeconds > AlertConstants.MAX_SECONDS) {
            throw invalid("离线时长取值需在 [0, 86400] 秒");
        }

        int suppressWindowSeconds = request.getSuppressWindowSeconds() == null
                ? 0 : request.getSuppressWindowSeconds();
        if (suppressWindowSeconds < 0 || suppressWindowSeconds > AlertConstants.MAX_SECONDS) {
            throw invalid("抑制窗口取值需在 [0, 86400] 秒");
        }

        int enabled = request.getEnabled() == null ? 1 : request.getEnabled();
        if (enabled != 0 && enabled != 1) {
            throw invalid("启用状态只能是 0 或 1");
        }

        String identifier = trimToNull(request.getIdentifier());
        String operator = normalizeUpper(request.getOperator());
        String thresholdValue = trimToNull(request.getThresholdValue());
        String eventType = trimToNull(request.getEventType());
        if (eventType != null) {
            eventType = eventType.toLowerCase();
        }

        if (AlertConstants.SOURCE_THRESHOLD.equals(sourceType)) {
            checkIdentifier(identifier, "阈值规则");
            if (operator == null || !AlertConstants.OPERATORS.contains(operator)) {
                throw invalid("比较符必须是 GT / GTE / LT / LTE / EQ / NE");
            }
            if (thresholdValue == null) {
                throw invalid("阈值规则必须填写阈值");
            }
            if (AlertConstants.NUMERIC_OPERATORS.contains(operator) && !isDecimal(thresholdValue)) {
                throw invalid("数值比较符下阈值必须是可解析的十进制数");
            }
            eventType = null;
            offlineSeconds = 0;
        } else if (AlertConstants.SOURCE_OFFLINE.equals(sourceType)) {
            identifier = null;
            operator = null;
            thresholdValue = null;
            eventType = null;
        } else {
            checkIdentifier(identifier, "事件规则");
            if (eventType != null && !AlertConstants.EVENT_TYPES.contains(eventType)) {
                throw invalid("事件类型必须是 info / alert / fault");
            }
            operator = null;
            thresholdValue = null;
            offlineSeconds = 0;
        }

        rule.setName(name);
        rule.setSourceType(sourceType);
        rule.setSeverity(severity);
        rule.setDeviceId(deviceId);
        rule.setIdentifier(identifier);
        rule.setOperator(operator);
        rule.setThresholdValue(thresholdValue);
        rule.setEventType(eventType);
        rule.setOfflineSeconds(offlineSeconds);
        rule.setSuppressWindowSeconds(suppressWindowSeconds);
        rule.setEnabled(enabled);
    }

    private static void checkIdentifier(String identifier, String ruleKind) {
        if (identifier == null) {
            throw invalid(ruleKind + "必须填写标识符");
        }
        if (identifier.length() > 64) {
            throw invalid("标识符长度不能超过 64");
        }
    }

    private static boolean isDecimal(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.ALERT_RULE_INVALID, message);
    }
}
