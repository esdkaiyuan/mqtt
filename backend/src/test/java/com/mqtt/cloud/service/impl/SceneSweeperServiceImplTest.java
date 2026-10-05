package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import com.mqtt.cloud.service.SceneActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 场景步骤巡检单测（T-23 设计文档 §14.1 / §8.9）。
 * <p>
 * 被测类 {@link SceneSweeperServiceImpl} 承载三个相互隔离的子任务，故按子任务分组覆盖：
 * <ul>
 *     <li><b>到期步骤拾取</b>：只投递不执行（巡检线程不阻塞）；投递计数与单条异常隔离；
 *         投递被拒（队列已满）仅记日志不外抛；批量上限取自配置且下限钳为 1。</li>
 *     <li><b>卡死步骤恢复</b>：尚有重试余额 → {@code resetStale} 复位为 {@code PENDING}；
 *         余额耗尽 → {@code claimPending} 抢到 {@code RUNNING} 后走 {@link SceneExecutionFinalizer}
 *         中止（原因固定为「执行超时」）；条件更新返回 0（已被其它副本处理）不重复推进。</li>
 *     <li><b>终态保留清理</b>：保留天数 ≤ 0 直接返回 0（不触碰 mapper）；分批删除直至某批不足一批。</li>
 * </ul>
 * <p>
 * 多副本并发安全在本类中体现为「{@code resetStale} / {@code claimPending} 返回 0 即让行」的断言。
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
class SceneSweeperServiceImplTest {

    private static final Long STEP_RUN_ID = 500L;
    private static final Long SECOND_STEP_RUN_ID = 501L;
    private static final Long EXECUTION_ID = 900L;

    private SceneStepRunMapper stepRunMapper;
    private SceneExecutionMapper executionMapper;
    private SceneActionExecutor sceneActionExecutor;
    private SceneExecutionFinalizer finalizer;
    private SceneProperties properties;
    private ThreadPoolTaskExecutor sceneExecutor;
    private SceneSweeperServiceImpl service;

    @BeforeEach
    void setUp() {
        stepRunMapper = mock(SceneStepRunMapper.class);
        executionMapper = mock(SceneExecutionMapper.class);
        sceneActionExecutor = mock(SceneActionExecutor.class);
        finalizer = mock(SceneExecutionFinalizer.class);
        properties = new SceneProperties();
        sceneExecutor = mock(ThreadPoolTaskExecutor.class);
        service = new SceneSweeperServiceImpl(stepRunMapper, executionMapper, sceneActionExecutor,
                finalizer, properties, sceneExecutor);
    }

    // ---------- 到期步骤拾取 ----------

    @Test
    void sweepDueSteps_should_return_zero_when_no_due_steps() {
        when(stepRunMapper.selectDueSteps(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        assertThat(service.sweepDueSteps()).isZero();
        verifyNoInteractions(sceneExecutor, sceneActionExecutor, finalizer);
    }

    @Test
    void sweepDueSteps_should_deliver_each_due_step() {
        when(stepRunMapper.selectDueSteps(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(dueRun(STEP_RUN_ID), dueRun(SECOND_STEP_RUN_ID)));
        // 让执行器同步跑投递进去的任务，验证投递的正是「只调 executeStep」
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(sceneExecutor).execute(any(Runnable.class));

        assertThat(service.sweepDueSteps()).isEqualTo(2);

        verify(sceneExecutor, times(2)).execute(any(Runnable.class));
        verify(sceneActionExecutor).executeStep(STEP_RUN_ID);
        verify(sceneActionExecutor).executeStep(SECOND_STEP_RUN_ID);
        verifyNoInteractions(finalizer);
    }

    @Test
    void sweepDueSteps_should_skip_step_when_delivery_throws_unexpectedly() {
        when(stepRunMapper.selectDueSteps(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(dueRun(STEP_RUN_ID), dueRun(SECOND_STEP_RUN_ID)));
        // 首条投递抛非拒绝异常（被子任务 catch 隔离），次条正常
        doThrow(new IllegalStateException("executor broken"))
                .doNothing()
                .when(sceneExecutor).execute(any(Runnable.class));

        assertThatCode(() -> assertThat(service.sweepDueSteps()).isEqualTo(1))
                .doesNotThrowAnyException();

        verify(sceneExecutor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void sweepDueSteps_should_swallow_rejected_delivery_without_failing() {
        when(stepRunMapper.selectDueSteps(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(dueRun(STEP_RUN_ID)));
        // 队列已满：deliver 内部吞掉拒绝，保留到期锚点留待下轮
        doThrow(new RejectedExecutionException("queue full"))
                .when(sceneExecutor).execute(any(Runnable.class));

        assertThatCode(() -> assertThat(service.sweepDueSteps()).isEqualTo(1))
                .doesNotThrowAnyException();

        verifyNoInteractions(sceneActionExecutor);
    }

    @Test
    void sweepDueSteps_should_use_configured_batch_size() {
        properties.setSweepBatchSize(50);
        when(stepRunMapper.selectDueSteps(any(LocalDateTime.class), eq(50))).thenReturn(List.of());

        service.sweepDueSteps();

        verify(stepRunMapper).selectDueSteps(any(LocalDateTime.class), eq(50));
    }

    @Test
    void sweepDueSteps_should_clamp_batch_size_to_at_least_one() {
        properties.setSweepBatchSize(0);
        when(stepRunMapper.selectDueSteps(any(LocalDateTime.class), eq(1))).thenReturn(List.of());

        service.sweepDueSteps();

        verify(stepRunMapper).selectDueSteps(any(LocalDateTime.class), eq(1));
    }

    // ---------- 卡死步骤恢复 ----------

    @Test
    void recoverStaleSteps_should_return_zero_when_no_stale_steps() {
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        assertThat(service.recoverStaleSteps()).isZero();
        verifyNoInteractions(finalizer);
    }

    @Test
    void recoverStaleSteps_should_reset_to_pending_when_retry_balance_remains() {
        SceneStepRun run = staleRun(STEP_RUN_ID, 0);
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of(run));
        when(stepRunMapper.resetStale(eq(STEP_RUN_ID), eq(1), any(LocalDateTime.class))).thenReturn(1);

        assertThat(service.recoverStaleSteps()).isEqualTo(1);

        verify(stepRunMapper).resetStale(eq(STEP_RUN_ID), eq(1), any(LocalDateTime.class));
        verify(stepRunMapper, never()).claimPending(any());
        verifyNoInteractions(finalizer);
    }

    @Test
    void recoverStaleSteps_should_treat_null_attempt_count_as_zero() {
        SceneStepRun run = staleRun(STEP_RUN_ID, null);
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of(run));
        when(stepRunMapper.resetStale(eq(STEP_RUN_ID), eq(1), any(LocalDateTime.class))).thenReturn(1);

        assertThat(service.recoverStaleSteps()).isEqualTo(1);

        verify(stepRunMapper).resetStale(eq(STEP_RUN_ID), eq(1), any(LocalDateTime.class));
    }

    @Test
    void recoverStaleSteps_should_skip_when_reset_guard_rejects() {
        SceneStepRun run = staleRun(STEP_RUN_ID, 1);
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of(run));
        // 状态守卫未命中（已被其它副本处理）
        when(stepRunMapper.resetStale(eq(STEP_RUN_ID), eq(2), any(LocalDateTime.class))).thenReturn(0);

        assertThat(service.recoverStaleSteps()).isZero();

        verify(stepRunMapper, never()).claimPending(any());
        verifyNoInteractions(finalizer);
    }

    @Test
    void recoverStaleSteps_should_abort_when_retry_exhausted() {
        SceneStepRun run = staleRun(STEP_RUN_ID, 3);
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of(run));
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenReturn(1);

        assertThat(service.recoverStaleSteps()).isEqualTo(1);

        verify(stepRunMapper, never()).resetStale(any(), anyInt(), any(LocalDateTime.class));
        verify(finalizer).abort(eq(run), eq(4), eq("执行超时"));
    }

    @Test
    void recoverStaleSteps_should_not_abort_when_claim_lost() {
        SceneStepRun run = staleRun(STEP_RUN_ID, 3);
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of(run));
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenReturn(0);

        assertThat(service.recoverStaleSteps()).isZero();

        verifyNoInteractions(finalizer);
    }

    @Test
    void recoverStaleSteps_should_use_step_timeout_as_deadline() {
        properties.setStepTimeoutMs(60_000L);
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        service.recoverStaleSteps();

        ArgumentCaptor<LocalDateTime> deadlineCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stepRunMapper).selectStaleRunning(deadlineCaptor.capture(), anyInt());
        LocalDateTime now = LocalDateTime.now();
        assertThat(deadlineCaptor.getValue())
                .isBefore(now.minusSeconds(30))
                .isAfter(now.minusSeconds(90));
    }

    @Test
    void recoverStaleSteps_should_skip_when_single_recover_throws() {
        SceneStepRun run = staleRun(STEP_RUN_ID, 0);
        when(stepRunMapper.selectStaleRunning(any(LocalDateTime.class), anyInt())).thenReturn(List.of(run));
        when(stepRunMapper.resetStale(eq(STEP_RUN_ID), eq(1), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> assertThat(service.recoverStaleSteps()).isZero())
                .doesNotThrowAnyException();
        verifyNoInteractions(finalizer);
    }

    // ---------- 终态保留清理 ----------

    @Test
    void purgeExpired_should_return_zero_when_retention_disabled() {
        properties.setExecutionRetentionDays(0);

        assertThat(service.purgeExpired()).isZero();
        verifyNoInteractions(executionMapper);
    }

    @Test
    void purgeExpired_should_return_zero_when_retention_negative() {
        properties.setExecutionRetentionDays(-1);

        assertThat(service.purgeExpired()).isZero();
        verifyNoInteractions(executionMapper);
    }

    @Test
    void purgeExpired_should_delete_until_partial_batch() {
        properties.setExecutionRetentionDays(30);
        properties.setSweepBatchSize(2);
        // 首批满（2 == batch）继续，次批不足（1 < batch）终止
        when(executionMapper.deleteFinishedBefore(any(LocalDateTime.class), eq(2))).thenReturn(2, 1);

        assertThat(service.purgeExpired()).isEqualTo(3);
        verify(executionMapper, times(2)).deleteFinishedBefore(any(LocalDateTime.class), eq(2));
    }

    @Test
    void purgeExpired_should_stop_when_first_batch_partial() {
        properties.setExecutionRetentionDays(30);
        properties.setSweepBatchSize(2);
        when(executionMapper.deleteFinishedBefore(any(LocalDateTime.class), eq(2))).thenReturn(1);

        assertThat(service.purgeExpired()).isEqualTo(1);
        verify(executionMapper, times(1)).deleteFinishedBefore(any(LocalDateTime.class), eq(2));
    }

    @Test
    void purgeExpired_should_use_retention_cutoff() {
        properties.setExecutionRetentionDays(7);
        when(executionMapper.deleteFinishedBefore(any(LocalDateTime.class), anyInt())).thenReturn(0);

        service.purgeExpired();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(executionMapper).deleteFinishedBefore(cutoffCaptor.capture(), anyInt());
        LocalDateTime expected = LocalDateTime.now().minusDays(7);
        assertThat(cutoffCaptor.getValue())
                .isBefore(expected.plusMinutes(1))
                .isAfter(expected.minusMinutes(1));
    }

    @Test
    void purgeExpired_should_clamp_batch_size_to_at_least_one() {
        properties.setExecutionRetentionDays(30);
        properties.setSweepBatchSize(0);
        when(executionMapper.deleteFinishedBefore(any(LocalDateTime.class), eq(1))).thenReturn(1, 0);

        assertThat(service.purgeExpired()).isEqualTo(1);
        verify(executionMapper, times(2)).deleteFinishedBefore(any(LocalDateTime.class), eq(1));
    }

    // ---------- 辅助 ----------

    private SceneStepRun dueRun(Long id) {
        SceneStepRun run = new SceneStepRun();
        run.setId(id);
        run.setExecutionId(EXECUTION_ID);
        run.setSeq(1);
        run.setStatus("PENDING");
        return run;
    }

    private SceneStepRun staleRun(Long id, Integer attemptCount) {
        SceneStepRun run = new SceneStepRun();
        run.setId(id);
        run.setExecutionId(EXECUTION_ID);
        run.setSeq(1);
        run.setStatus("RUNNING");
        run.setAttemptCount(attemptCount);
        return run;
    }
}
