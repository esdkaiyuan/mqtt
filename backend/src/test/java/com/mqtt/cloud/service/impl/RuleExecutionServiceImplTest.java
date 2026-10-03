package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.mapper.RuleExecutionMapper;
import com.mqtt.cloud.service.RuleActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则执行记录服务单测（T-19 实施计划 P7）。
 * <p>
 * 覆盖归属隔离（不存在 {@code 6221} / 越权 {@code 403}）、手动重试仅允许 {@code FAILED}
 * （非失败与条件更新影响 0 行均返回 {@code 409}）、重置后投递执行与队列满兜底置失败。
 */
class RuleExecutionServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long EXECUTION_ID = 900L;

    private RuleExecutionMapper executionMapper;
    private RuleActionExecutor ruleActionExecutor;
    private ThreadPoolTaskExecutor ruleExecutor;
    private RuleExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        executionMapper = mock(RuleExecutionMapper.class);
        ruleActionExecutor = mock(RuleActionExecutor.class);
        ruleExecutor = mock(ThreadPoolTaskExecutor.class);
        service = new RuleExecutionServiceImpl(executionMapper, ruleActionExecutor, ruleExecutor);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(ruleExecutor).execute(any(Runnable.class));
    }

    @Test
    void getOwned_should_throw_when_missing() {
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(null);

        assertCode(() -> service.getOwned(USER_ID, EXECUTION_ID), ResultCode.RULE_EXECUTION_NOT_FOUND);
    }

    @Test
    void getOwned_should_throw_when_not_owned() {
        RuleExecution execution = execution(RuleConstants.STATUS_FAILED);
        execution.setUserId(999L);
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(execution);

        assertCode(() -> service.getOwned(USER_ID, EXECUTION_ID), ResultCode.FORBIDDEN);
    }

    @Test
    void manualRetry_should_reject_non_failed_status() {
        when(executionMapper.selectById(EXECUTION_ID))
                .thenReturn(execution(RuleConstants.STATUS_PENDING));

        assertThatThrownBy(() -> service.manualRetry(USER_ID, EXECUTION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.RULE_INVALID.getCode());
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(executionMapper, never()).resetForRetry(any());
    }

    @Test
    void manualRetry_should_conflict_when_reset_affects_zero_rows() {
        when(executionMapper.selectById(EXECUTION_ID))
                .thenReturn(execution(RuleConstants.STATUS_FAILED));
        when(executionMapper.resetForRetry(EXECUTION_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.manualRetry(USER_ID, EXECUTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void manualRetry_should_reset_and_dispatch() {
        when(executionMapper.selectById(EXECUTION_ID))
                .thenReturn(execution(RuleConstants.STATUS_FAILED));
        when(executionMapper.resetForRetry(EXECUTION_ID)).thenReturn(1);

        service.manualRetry(USER_ID, EXECUTION_ID);

        verify(executionMapper).resetForRetry(EXECUTION_ID);
        verify(ruleExecutor).execute(any(Runnable.class));
        verify(ruleActionExecutor).execute(EXECUTION_ID);
    }

    @Test
    void manualRetry_should_mark_failed_when_dispatch_rejected() {
        when(executionMapper.selectById(EXECUTION_ID))
                .thenReturn(execution(RuleConstants.STATUS_FAILED));
        when(executionMapper.resetForRetry(EXECUTION_ID)).thenReturn(1);
        doThrow(new RejectedExecutionException("full")).when(ruleExecutor).execute(any(Runnable.class));

        service.manualRetry(USER_ID, EXECUTION_ID);

        verify(executionMapper).markFailed(eq(EXECUTION_ID), eq(0), anyString(), any());
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private RuleExecution execution(String status) {
        RuleExecution execution = new RuleExecution();
        execution.setId(EXECUTION_ID);
        execution.setUserId(USER_ID);
        execution.setStatus(status);
        execution.setAttemptCount(1);
        return execution;
    }
}