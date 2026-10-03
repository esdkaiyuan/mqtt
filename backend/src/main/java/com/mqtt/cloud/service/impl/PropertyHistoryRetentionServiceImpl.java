package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.PropertyHistoryConstants;
import com.mqtt.cloud.config.PropertyHistoryProperties;
import com.mqtt.cloud.mapper.DevicePropertyHistoryMapper;
import com.mqtt.cloud.service.PropertyHistoryRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 属性历史保留清理实现（T-21 设计文档 §5.4 / §7.1）。
 * <p>
 * 与 {@code RuleSweeperServiceImpl} 同款分批循环：{@code while (true)} 逐批硬删除，
 * 单批影响行数小于批量即视为本轮清空；批大小至少 1，配置非正时兜底为常量默认值。
 * 线程安全：清理与写入互不阻塞（历史表为插入密集、删除按 {@code idx_reported_at} 走索引）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyHistoryRetentionServiceImpl implements PropertyHistoryRetentionService {

    private final DevicePropertyHistoryMapper historyMapper;
    private final PropertyHistoryProperties properties;

    @Override
    public int purgeExpired() {
        int retentionDays = retentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        int batch = batchSize();
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        while (true) {
            int deleted = historyMapper.purgeBefore(threshold, batch);
            total += deleted;
            if (deleted < batch) {
                return total;
            }
        }
    }

    private int retentionDays() {
        int configured = properties.getRetentionDays();
        return configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_RETENTION_DAYS;
    }

    private int batchSize() {
        int configured = properties.getCleanupBatchSize();
        int effective = configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_CLEANUP_BATCH_SIZE;
        return Math.max(1, effective);
    }
}
