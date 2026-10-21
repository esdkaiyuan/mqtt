package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import com.mqtt.cloud.service.SceneActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 场景执行记录服务：手动重试的并行块重置口径（T-49）。
 * <p>
 * 并行块内任一步骤失败后，不能只重放该分支，否则其它已成功分支不会被重新计数、汇合条件永远不满足；
 * 因此失败步骤位于并行块内时，必须从 {@code PARALLEL} 步骤整体重置该块。
 */
class SceneExecutionServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long EXECUTION_ID = 900L;
    private static final Long PARALLEL_RUN_ID = 600L;
    private static final Long BRANCH_A_RUN_ID = 601L;
    private static final Long PLAIN_RUN_ID = 605L;

    private SceneExecutionMapper executionMapper;
    private SceneStepRunMapper stepRunMapper;
    private SceneActionExecutor actionExecutor;
    private ThreadPoolTaskExecutor sceneExecutor;
    private SceneExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        executionMapper = mock(SceneExecutionMapper.class);
        stepRunMapper = mock(SceneStepRunMapper.class);
        actionExecutor = mock(SceneActionExecutor.class);
        sceneExecutor = mock(ThreadPoolTaskExecutor.class);
        service = new SceneExecutionServiceImpl(executionMapper, stepRunMapper, actionExecutor, sceneExecutor);
    }

    @Test
    void manual_retry_should_reset_parallel_block_from_parallel_start() {
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(failedExecution());
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(
                run(PARALLEL_RUN_ID, 1, SceneConstants.STEP_PARALLEL, null, null, SceneConstants.STEP_SUCCESS),
                run(BRANCH_A_RUN_ID, 2, SceneConstants.ACTION_UPDATE_PROPERTY, 1, 0, SceneConstants.STEP_FAILED),
                run(602L, 3, SceneConstants.ACTION_UPDATE_PROPERTY, 1, 1, SceneConstants.STEP_SUCCESS),
                run(603L, 4, SceneConstants.STEP_JOIN, null, null, SceneConstants.STEP_SKIPPED)));
        when(executionMapper.resetForRetry(EXECUTION_ID)).thenReturn(1);

        service.manualRetry(USER_ID, EXECUTION_ID);

        verify(stepRunMapper).resetForRetry(eq(EXECUTION_ID), eq(1), any());
        verify(stepRunMapper).resetParallelFrom(EXECUTION_ID, 1);
        verify(stepRunMapper).scheduleNext(eq(PARALLEL_RUN_ID), any(), any());
        verify(executionMapper).recountFinishedSteps(EXECUTION_ID);
        verify(sceneExecutor).execute(any());
    }

    @Test
    void manual_retry_after_completed_block_should_resume_from_failed_step() {
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(failedExecution());
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(
                run(600L, 1, SceneConstants.STEP_PARALLEL, null, null, SceneConstants.STEP_SUCCESS),
                run(601L, 2, SceneConstants.ACTION_UPDATE_PROPERTY, 1, 0, SceneConstants.STEP_SUCCESS),
                run(602L, 3, SceneConstants.ACTION_UPDATE_PROPERTY, 1, 1, SceneConstants.STEP_SUCCESS),
                run(603L, 4, SceneConstants.STEP_JOIN, null, null, SceneConstants.STEP_SUCCESS),
                run(PLAIN_RUN_ID, 5, SceneConstants.ACTION_UPDATE_PROPERTY, null, null, SceneConstants.STEP_FAILED)));
        when(executionMapper.resetForRetry(EXECUTION_ID)).thenReturn(1);

        service.manualRetry(USER_ID, EXECUTION_ID);

        verify(stepRunMapper).resetForRetry(eq(EXECUTION_ID), eq(5), any());
        verify(stepRunMapper).resetParallelFrom(EXECUTION_ID, 5);
        verify(stepRunMapper, never()).scheduleNext(eq(600L), any(), any());
        verify(sceneExecutor).execute(any());
    }

    private SceneExecution failedExecution() {
        SceneExecution execution = new SceneExecution();
        execution.setId(EXECUTION_ID);
        execution.setUserId(USER_ID);
        execution.setStatus(SceneConstants.EXEC_FAILED);
        return execution;
    }

    private SceneStepRun run(Long id, int seq, String actionType,
                             Integer parallelSeq, Integer branchIndex, String status) {
        SceneStepRun run = new SceneStepRun();
        run.setId(id);
        run.setExecutionId(EXECUTION_ID);
        run.setSeq(seq);
        run.setActionType(actionType);
        run.setParallelSeq(parallelSeq);
        run.setBranchIndex(branchIndex);
        run.setStatus(status);
        return run;
    }
}
