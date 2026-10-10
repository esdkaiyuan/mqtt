package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.dto.request.AlertEscalationPolicyRequest;
import com.mqtt.cloud.entity.AlertEscalationPolicy;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.mapper.AlertEscalationPolicyMapper;
import com.mqtt.cloud.mapper.UserMapper;
import com.mqtt.cloud.service.AlertEscalationService;
import com.mqtt.cloud.service.NotificationChannelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 告警升级策略服务实现（T-27 设计文档 §6.4 / §7）。
 * <p>
 * 校验矩阵集中在此：名称 / 级别 / 阈值 / 处理人 / 渠道 / 数量命中即抛 {@code 6266}。
 * 处理人须为存在的用户，渠道须为本人所有 —— 后者防止把升级通知投到他人渠道。
 */
@Slf4j
@Service
public class AlertEscalationServiceImpl
        extends ServiceImpl<AlertEscalationPolicyMapper, AlertEscalationPolicy>
        implements AlertEscalationService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_AFTER_SECONDS = 60;
    /** 适用级别白名单：ALL 表示不限级别。 */
    private static final Set<String> SEVERITY_SCOPES = Set.of("ALL",
            AlertConstants.SEVERITY_INFO, AlertConstants.SEVERITY_WARNING, AlertConstants.SEVERITY_CRITICAL);

    private final NotificationProperties properties;
    private final UserMapper userMapper;
    private final NotificationChannelConfigService channelConfigService;
    private final ObjectMapper objectMapper;

    public AlertEscalationServiceImpl(NotificationProperties properties,
                                      UserMapper userMapper,
                                      NotificationChannelConfigService channelConfigService,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.channelConfigService = channelConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AlertEscalationPolicy> listByUser(Long userId) {
        LambdaQueryWrapper<AlertEscalationPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEscalationPolicy::getUserId, userId)
                .orderByDesc(AlertEscalationPolicy::getId);
        return list(wrapper);
    }

    @Override
    public AlertEscalationPolicy getOwned(Long userId, Long id) {
        return requireOwned(userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertEscalationPolicy create(Long userId, AlertEscalationPolicyRequest request) {
        String name = requireName(request.getName());
        String severity = normalizeSeverity(request.getSeverity());
        int afterSeconds = requireAfterSeconds(request.getAfterSeconds());
        Long assigneeId = validateAssignee(request.getAssigneeId());
        String channelIds = validateChannelIds(userId, request.getChannelIds());
        validateCount(userId);

        AlertEscalationPolicy entity = new AlertEscalationPolicy();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setSeverity(severity);
        entity.setAfterSeconds(afterSeconds);
        entity.setAssigneeId(assigneeId);
        entity.setChannelIds(channelIds);
        entity.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        save(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertEscalationPolicy update(Long userId, Long id, AlertEscalationPolicyRequest request) {
        AlertEscalationPolicy existing = requireOwned(userId, id);
        String name = requireName(request.getName());
        String severity = normalizeSeverity(request.getSeverity());
        int afterSeconds = requireAfterSeconds(request.getAfterSeconds());
        Long assigneeId = validateAssignee(request.getAssigneeId());
        String channelIds = validateChannelIds(userId, request.getChannelIds());

        LambdaUpdateWrapper<AlertEscalationPolicy> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AlertEscalationPolicy::getId, id)
                .eq(AlertEscalationPolicy::getUserId, userId)
                .set(AlertEscalationPolicy::getName, name)
                .set(AlertEscalationPolicy::getSeverity, severity)
                .set(AlertEscalationPolicy::getAfterSeconds, afterSeconds)
                .set(AlertEscalationPolicy::getAssigneeId, assigneeId)
                .set(AlertEscalationPolicy::getChannelIds, channelIds)
                .set(AlertEscalationPolicy::getEnabled, Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        update(wrapper);

        existing.setName(name);
        existing.setSeverity(severity);
        existing.setAfterSeconds(afterSeconds);
        existing.setAssigneeId(assigneeId);
        existing.setChannelIds(channelIds);
        existing.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        removeById(id);
    }

    @Override
    public List<AlertEscalationPolicy> listEnabled(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        LambdaQueryWrapper<AlertEscalationPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEscalationPolicy::getEnabled, 1)
                .orderByAsc(AlertEscalationPolicy::getId);
        Page<AlertEscalationPolicy> page = new Page<>(1, limit, false);
        return baseMapper.selectPage(page, wrapper).getRecords();
    }

    private AlertEscalationPolicy requireOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_NOT_FOUND);
        }
        AlertEscalationPolicy entity = getById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_NOT_FOUND);
        }
        if (!userId.equals(entity.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return entity;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID, "策略名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID, "策略名称不能超过 100 个字符");
        }
        return trimmed;
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID, "适用级别不能为空");
        }
        String normalized = severity.trim().toUpperCase();
        if (!SEVERITY_SCOPES.contains(normalized)) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID, "不支持的适用级别: " + severity);
        }
        return normalized;
    }

    private int requireAfterSeconds(Integer afterSeconds) {
        if (afterSeconds == null || afterSeconds < MIN_AFTER_SECONDS) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID,
                    "未确认时长阈值须为不小于 " + MIN_AFTER_SECONDS + " 的整数");
        }
        return afterSeconds;
    }

    private Long validateAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        User user = userMapper.selectById(assigneeId);
        if (user == null) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID, "处理人不存在: " + assigneeId);
        }
        return assigneeId;
    }

    private String validateChannelIds(Long userId, List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return null;
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long channelId : channelIds) {
            if (channelId == null) {
                continue;
            }
            NotificationChannelConfig channel = channelConfigService.getRawById(channelId);
            if (channel == null || !userId.equals(channel.getUserId())) {
                throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID, "通知渠道不存在或非本人: " + channelId);
            }
            unique.add(channelId);
        }
        return unique.isEmpty() ? null : toJson(new ArrayList<>(unique));
    }

    private void validateCount(Long userId) {
        LambdaQueryWrapper<AlertEscalationPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEscalationPolicy::getUserId, userId);
        if (count(wrapper) >= properties.getMaxEscalationPoliciesPerUser()) {
            throw new BusinessException(ResultCode.ESCALATION_POLICY_INVALID,
                    "升级策略数量已达上限 " + properties.getMaxEscalationPoliciesPerUser());
        }
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}