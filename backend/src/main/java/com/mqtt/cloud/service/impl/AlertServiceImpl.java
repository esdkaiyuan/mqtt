package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.AlertAssignRequest;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.UserMapper;
import com.mqtt.cloud.service.AlertNotifier;
import com.mqtt.cloud.service.AlertService;
import com.mqtt.cloud.service.NotificationDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警记录服务实现（T-17 设计文档 §10.1）。
 * <p>
 * 记录按 {@code user_id} 隔离，读写前先做归属校验；状态迁移走 Mapper 的条件更新，
 * 受影响行数为 0 即视为「当前状态不允许该操作」，统一抛 {@code 6210}，从而在多副本
 * 并发下保持幂等。人工恢复后发 {@code alert.recovered} 通知（通知内部自行隔离异常）。
 */
@Service
public class AlertServiceImpl extends ServiceImpl<AlertRecordMapper, AlertRecord> implements AlertService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DeviceMapper deviceMapper;
    private final UserMapper userMapper;
    private final AlertNotifier alertNotifier;
    private final NotificationDispatcher notificationDispatcher;

    public AlertServiceImpl(DeviceMapper deviceMapper, UserMapper userMapper,
                            AlertNotifier alertNotifier, NotificationDispatcher notificationDispatcher) {
        this.deviceMapper = deviceMapper;
        this.userMapper = userMapper;
        this.alertNotifier = alertNotifier;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Override
    public IPage<AlertRecord> page(Long userId, AlertQuery query) {
        AlertQuery effective = query == null ? new AlertQuery() : query;
        int pageNum = (effective.getPageNum() == null || effective.getPageNum() < 1)
                ? 1 : effective.getPageNum();
        int pageSize = (effective.getPageSize() == null || effective.getPageSize() < 1)
                ? DEFAULT_PAGE_SIZE : Math.min(effective.getPageSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getUserId, userId);
        String status = normalizeUpper(effective.getStatus());
        if (status != null) {
            wrapper.eq(AlertRecord::getStatus, status);
        } else if (Boolean.TRUE.equals(effective.getOpenOnly())) {
            wrapper.ne(AlertRecord::getStatus, AlertConstants.STATUS_RECOVERED);
        }
        String sourceType = normalizeUpper(effective.getSourceType());
        if (sourceType != null) {
            wrapper.eq(AlertRecord::getSourceType, sourceType);
        }
        String severity = normalizeUpper(effective.getSeverity());
        if (severity != null) {
            wrapper.eq(AlertRecord::getSeverity, severity);
        }
        if (effective.getDeviceId() != null) {
            wrapper.eq(AlertRecord::getDeviceId, effective.getDeviceId());
        }
        wrapper.orderByDesc(AlertRecord::getLastTriggeredAt).orderByDesc(AlertRecord::getId);
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public AlertRecord getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND);
        }
        AlertRecord record = getById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND);
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acknowledge(Long userId, Long id) {
        AlertRecord record = getOwned(userId, id);
        int rows = baseMapper.markAcknowledged(record.getId(), LocalDateTime.now(), userId);
        if (rows == 0) {
            throw new BusinessException(ResultCode.ALERT_STATUS_INVALID, "仅待处理的告警可确认");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long userId, Long id) {
        AlertRecord record = getOwned(userId, id);
        LocalDateTime now = LocalDateTime.now();
        int rows = baseMapper.markRecovered(record.getId(), now);
        if (rows == 0) {
            throw new BusinessException(ResultCode.ALERT_STATUS_INVALID, "该告警已恢复");
        }
        record.setStatus(AlertConstants.STATUS_RECOVERED);
        record.setRecoveredAt(now);
        alertNotifier.notifyRecovered(record, deviceMapper.selectById(record.getDeviceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long userId, Long id, AlertAssignRequest request) {
        AlertRecord record = getOwned(userId, id);
        Long assigneeId = request == null ? null : request.getAssigneeId();
        if (assigneeId != null && userMapper.selectById(assigneeId) == null) {
            throw new BusinessException(ResultCode.ALERT_ASSIGNEE_INVALID, "处理人不存在: " + assigneeId);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = baseMapper.assign(record.getId(), userId, assigneeId, now);
        if (rows == 0) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND);
        }
        record.setAssigneeId(assigneeId);
        record.setUpdatedAt(now);
        if (Boolean.TRUE.equals(request.getNotify())) {
            notifyAssignee(record);
        }
    }

    /** 指派通知：投递一次 {@code alert.escalated}，异常只记 WARN，不影响指派落库。 */
    private void notifyAssignee(AlertRecord record) {
        Device device = record.getDeviceId() == null ? null : deviceMapper.selectById(record.getDeviceId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", record.getId());
        payload.put("ruleId", record.getRuleId());
        payload.put("ruleName", record.getRuleName());
        payload.put("sourceType", record.getSourceType());
        payload.put("severity", record.getSeverity());
        payload.put("title", record.getTitle());
        payload.put("assigneeId", record.getAssigneeId());
        payload.put("escalationLevel", record.getEscalationLevel());
        payload.put("assignedAt", TIME_FORMATTER.format(LocalDateTime.now()));
        notificationDispatcher.dispatch(record.getUserId(), AlertConstants.EVENT_ALERT_ESCALATED,
                device, record, payload);
    }

    @Override
    public long unreadCount(Long userId) {
        return baseMapper.countOpenByUser(userId);
    }

    @Override
    public List<AlertRecord> recent(Long userId, int limit) {
        int effective = limit <= 0 ? DEFAULT_RECENT_LIMIT : Math.min(limit, MAX_PAGE_SIZE);
        return baseMapper.selectOpenByUserLimit(userId, effective);
    }

    private static String normalizeUpper(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }
}