package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.mapper.RuleExecutionMapper;
import com.mqtt.cloud.service.RuleActionExecutor;
import com.mqtt.cloud.service.RuleSweeperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则重试巡检实现（T-19 设计文档 §8.6）。
 * <p>
 * 重放逐条 {@code try/catch} 隔离：单条异常不影响其余记录；并发保护由
 * {@code markSuccess / markRetry / markFailed} 的 {@code status='PENDING'} 条件更新兜底，
 * 多副本巡检不会重复成功。保留清理只删终态记录，按批循环直至单批不足批量。
 */
@Slf4j
@Service
public class RuleSweeperServiceImpl implements RuleSweeperService {

    private final RuleExecutionMapper executionMapper;
    private final RuleActionExecutor ruleActionExecutor;
    private final RuleProperties properties;

    public RuleSweeperServiceImpl(RuleExecutionMapper executionMapper,
                                  RuleActionExecutor ruleActionExecutor,
                                  RuleProperties properties) {
        this.executionMapper = executionMapper;
        this.ruleActionExecutor = ruleActionExecutor;
        this.properties = properties;
    }

    @Override
    public int sweepRetries() {
        int batch = batchSize();
        List<RuleExecution> due = executionMapper.selectDueRetries(LocalDateTime.now(), batch);
        if (due.isEmpty()) {
            return 0;
        }
        int replayed = 0;
        for (RuleExecution execution : due) {
            try {
                ruleActionExecutor.execute(execution.getId());
                replayed++;
            } catch (Exception e) {
                log.warn("规则重试执行异常，跳过: executionId={}", execution.getId(), e);
            }
        }
        return replayed;
    }

    @Override
    public int purgeExpired() {
        int retentionDays = properties.getExecutionRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        int batch = batchSize();
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        while (true) {
            int deleted = executionMapper.deleteFinishedBefore(before, batch);
            total += deleted;
            if (deleted < batch) {
                return total;
            }
        }
    }

    private int batchSize() {
        return Math.max(1, properties.getSweepBatchSize());
    }
}