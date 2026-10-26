package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.audit.AuditRequestInfo;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.util.PageSupport;
import com.mqtt.cloud.config.AuditLogProperties;
import com.mqtt.cloud.dto.request.AuditLogQueryDTO;
import com.mqtt.cloud.entity.AuditLog;
import com.mqtt.cloud.mapper.AuditLogMapper;
import com.mqtt.cloud.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 审计日志实现（T-53）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final DateTimeFormatter SPACE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_EXPORT_LIMIT = 10000;
    private static final int MAX_EXPORT_LIMIT = 100000;

    private final AuditLogMapper auditLogMapper;
    private final AuditLogProperties properties;

    @Override
    public void record(AuditLog log) {
        if (log == null) {
            return;
        }
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(LocalDateTime.now());
        }
        log.setSource(log.getSource() == null ? "ANONYMOUS" : log.getSource());
        log.setHttpMethod(cut(log.getHttpMethod(), 8));
        log.setPath(cut(log.getPath(), 512));
        log.setAction(log.getAction() == null ? "OTHER" : cut(log.getAction(), 32));
        log.setResourceType(log.getResourceType() == null ? "OTHER" : cut(log.getResourceType(), 32));
        log.setResourceId(cut(log.getResourceId(), 128));
        log.setUsername(cut(log.getUsername(), 64));
        log.setRole(cut(log.getRole(), 32));
        log.setIp(cut(log.getIp(), 64));
        log.setUserAgent(cut(log.getUserAgent(), 255));
        log.setErrorMessage(cut(log.getErrorMessage(), 512));
        log.setStatusCode(log.getStatusCode() == null ? 200 : log.getStatusCode());
        log.setSuccess(log.getSuccess() == null || log.getSuccess());
        log.setDurationMs(log.getDurationMs() == null ? 0L : log.getDurationMs());
        auditLogMapper.insert(log);
    }

    @Override
    public IPage<AuditLog> page(AuditLogQueryDTO query) {
        AuditLogQueryDTO effective = query == null ? new AuditLogQueryDTO() : query;
        Page<AuditLog> page = new Page<>(PageSupport.page(effective.getPageNum()),
                PageSupport.size(effective.getPageSize(), 20));
        return auditLogMapper.selectPage(page, buildWrapper(effective));
    }

    @Override
    public List<AuditLog> listForExport(AuditLogQueryDTO query, int limit) {
        AuditLogQueryDTO effective = query == null ? new AuditLogQueryDTO() : query;
        int size = limit <= 0 ? DEFAULT_EXPORT_LIMIT : Math.min(limit, MAX_EXPORT_LIMIT);
        Page<AuditLog> page = new Page<>(1, size, false);
        return auditLogMapper.selectPage(page, buildWrapper(effective)).getRecords();
    }

    @Override
    public int purgeExpired() {
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        int batch = Math.max(1, properties.getPurgeBatchSize());
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        while (true) {
            int deleted = auditLogMapper.deleteBefore(threshold, batch);
            total += deleted;
            if (deleted < batch) {
                return total;
            }
        }
    }

    private LambdaQueryWrapper<AuditLog> buildWrapper(AuditLogQueryDTO query) {
        LocalDateTime start = parseTime(query.getStartTime(), "startTime");
        LocalDateTime end = parseTime(query.getEndTime(), "endTime");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ResultCode.AUDIT_LOG_QUERY_INVALID, "startTime 不能晚于 endTime");
        }

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, AuditLog::getUserId, query.getUserId())
                .like(hasText(query.getUsername()), AuditLog::getUsername, query.getUsername())
                .eq(hasText(query.getAction()), AuditLog::getAction, query.getAction())
                .eq(hasText(query.getResourceType()), AuditLog::getResourceType, query.getResourceType())
                .eq(hasText(query.getSource()), AuditLog::getSource, query.getSource())
                .eq(query.getSuccess() != null, AuditLog::getSuccess, query.getSuccess())
                .ge(start != null, AuditLog::getCreatedAt, start)
                .le(end != null, AuditLog::getCreatedAt, end);
        if (hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AuditLog::getPath, keyword)
                    .or().like(AuditLog::getUsername, keyword)
                    .or().like(AuditLog::getResourceId, keyword));
        }
        wrapper.orderByDesc(AuditLog::getCreatedAt).orderByDesc(AuditLog::getId);
        return wrapper;
    }

    /**
     * 时间解析容忍两种常见写法：控制台表格的 {@code yyyy-MM-dd HH:mm:ss} 与 ISO-8601。
     * 非法值抛业务异常而不是静默忽略——查询条件写错时返回全量数据比报错更危险。
     */
    private LocalDateTime parseTime(String raw, String field) {
        if (!hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDateTime.parse(value, SPACE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试 ISO-8601
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.AUDIT_LOG_QUERY_INVALID,
                    field + " 格式非法，应为 yyyy-MM-dd HH:mm:ss 或 ISO-8601");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String cut(String value, int maxLength) {
        return AuditRequestInfo.truncate(value, maxLength);
    }
}