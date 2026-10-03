package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.mapper.RuleExecutionMapper;
import com.mqtt.cloud.service.RuleActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则重试巡检单测（T-19 实施计划 P7）。
 * <p>
 * 覆盖到期重放、逐条异常隔离、保留清理按批循环与关闭策略；并发保护由 Mapper 条件更新保证，
 * 本层只断言「是否按预期重放 / 清理」。
 */
class RuleSweeperServiceImplTest {

    private static final int BATCH = 2;

    private RuleExecutionMapper executionMapper;
    private RuleActionExecutor ruleActionExecutor;
    private RuleProperties properties;
    private RuleSweeperServiceImpl service;

    @BeforeEach
    void setUp() {
        executionMapper = mock(RuleExecutionMapper.class);
        ruleActionExecutor = mock(RuleActionExecutor.class);
        properties = new RuleProperties();
        properties.setSweepBatchSize(BATCH);
        service = new RuleSweeperServiceImpl(executionMapper, ruleActionExecutor, properties);
    }

    @Test
    void sweepRetries_should_replay_due_records() {
        when(executionMapper.selectDueRetries(any(), eq(BATCH)))
                .thenReturn(List.of(execution(1L), execution(2L)));

        int replayed = service.sweepRetries();

        assertThat(replayed).isEqualTo(2);
        verify(ruleActionExecutor).execute(1L);
        verify(ruleActionExecutor).execute(2L);
    }

    @Test
    void sweepRetries_should_return_zero_when_none_due() {
        when(executionMapper.selectDueRetries(any(), eq(BATCH))).thenReturn(List.of());

        assertThat(service.sweepRetries()).isZero();
        verify(ruleActionExecutor, never()).execute(any());
    }

    @Test
    void sweepRetries_should_isolate_single_failure() {
        when(executionMapper.selectDueRetries(any(), eq(BATCH)))
                .thenReturn(List.of(execution(1L), execution(2L)));
        doThrow(new RuntimeException("boom")).when(ruleActionExecutor).execute(1L);

        int replayed = service.sweepRetries();

        assertThat(replayed).isEqualTo(1);
        verify(ruleActionExecutor).execute(2L);
    }

    @Test
    void purgeExpired_should_noop_when_retention_disabled() {
        properties.setExecutionRetentionDays(0);

        assertThat(service.purgeExpired()).isZero();
        verify(executionMapper, never()).deleteFinishedBefore(any(), anyInt());
    }

    @Test
    void purgeExpired_should_loop_until_under_batch() {
        properties.setExecutionRetentionDays(30);
        when(executionMapper.deleteFinishedBefore(any(), eq(BATCH))).thenReturn(2, 2, 1);

        assertThat(service.purgeExpired()).isEqualTo(5);
        verify(executionMapper, times(3)).deleteFinishedBefore(any(), eq(BATCH));
    }

    @Test
    void purgeExpired_should_stop_when_batch_empty() {
        properties.setExecutionRetentionDays(30);
        when(executionMapper.deleteFinishedBefore(any(), eq(BATCH))).thenReturn(0);

        assertThat(service.purgeExpired()).isZero();
        verify(executionMapper, times(1)).deleteFinishedBefore(any(), eq(BATCH));
    }

    private RuleExecution execution(Long id) {
        RuleExecution execution = new RuleExecution();
        execution.setId(id);
        return execution;
    }
}