package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.mapper.AlertRecordMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.AlertNotifier;
import com.mqtt.cloud.service.AlertService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警记录服务实现（T-17 设计文档 §10.1）。
 * <p>
 * 记录按 {@code user_id} 隔离，读写前先做归属校验；状态迁移走 Mapper 的条件更新，
 * 受影响行数为 0 即视为「当前状态不允许该操作」，统一抛 {@code 6210}，从而在多副本
 * 并发下保持幂等。人工恢复后发 {@code alert.recovered} 通知（通知内部自行隔离异常）。
 */
@Service
public class AlertServiceImpl extends ServiceImpl<AlertRecordMapper, AlertRecord> implements AlertService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DeviceMapper deviceMapper;
    private final AlertNotifier alertNotifier;

    public AlertServiceImpl(DeviceMapper deviceMapper, AlertNotifier alertNotifier) {
        this.deviceMapper = deviceMapper;
        this.alertNotifier = alertNotifier;
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
    public long unreadCount(Long userId) {
        return baseMapper.countOpenByUser(userId);
    }

    @Override
    public List<AlertRecord> recent(Long userId, int limit) {
        int effective = limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
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