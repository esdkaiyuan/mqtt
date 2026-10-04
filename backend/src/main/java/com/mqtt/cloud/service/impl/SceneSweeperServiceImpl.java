package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import com.mqtt.cloud.service.SceneActionExecutor;
import com.mqtt.cloud.service.SceneSweeperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * 场景步骤巡检实现（T-23 设计文档 §8.9）。
 * <p>
 * 三个子任务各自 {@code try/catch} 隔离：到期拾取只投递不执行（巡检线程不阻塞），卡死恢复区分
 * 「尚有重试余额 → {@code resetStale} 回 {@code PENDING}」与「余额耗尽 → {@link SceneExecutionFinalizer}
 * 中止」，保留清理只删终态执行记录（{@code PENDING}/{@code RUNNING} 永不清理）。
 * <p>
 * 多副本并发安全：到期拾取靠步骤执行器的 {@code claimPending} 条件更新抢占，卡死恢复靠 {@code resetStale}
 * 的 {@code status='RUNNING'} 守卫，均不会重复推进。
 */
@Slf4j
@Service
public class SceneSweeperServiceImpl implements SceneSweeperService {

    private final SceneStepRunMapper stepRunMapper;
    private final SceneExecutionMapper executionMapper;
    private final SceneActionExecutor sceneActionExecutor;
    private final SceneExecutionFinalizer finalizer;
    private final SceneProperties properties;
    private final ThreadPoolTaskExecutor sceneExecutor;

    public SceneSweeperServiceImpl(SceneStepRunMapper stepRunMapper,
                                   SceneExecutionMapper executionMapper,
                                   SceneActionExecutor sceneActionExecutor,
                                   SceneExecutionFinalizer finalizer,
                                   SceneProperties properties,
                                   @Qualifier("sceneExecutor") ThreadPoolTaskExecutor sceneExecutor) {
        this.stepRunMapper = stepRunMapper;
        this.executionMapper = executionMapper;
        this.sceneActionExecutor = sceneActionExecutor;
        this.finalizer = finalizer;
        this.properties = properties;
        this.sceneExecutor = sceneExecutor;
    }

    @Override
    public int sweepDueSteps() {
        int batch = batchSize();
        List<SceneStepRun> due = stepRunMapper.selectDueSteps(LocalDateTime.now(), batch);
        if (due.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SceneStepRun run : due) {
            try {
                deliver(run.getId());
                delivered++;
            } catch (Exception e) {
                log.warn("场景到期步骤投递异常，跳过: stepRunId={}", run.getId(), e);
            }
        }
        return delivered;
    }

    @Override
    public int recoverStaleSteps() {
        int batch = batchSize();
        LocalDateTime deadline = LocalDateTime.now().minusNanos(Math.max(1L, properties.getStepTimeoutMs()) * 1_000_000L);
        List<SceneStepRun> stale = stepRunMapper.selectStaleRunning(deadline, batch);
        if (stale.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (SceneStepRun run : stale) {
            try {
                if (recover(run)) {
                    recovered++;
                }
            } catch (Exception e) {
                log.warn("场景卡死步骤恢复异常，跳过: stepRunId={}", run.getId(), e);
            }
        }
        return recovered;
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

    /**
     * 单条卡死恢复：尚有重试余额则复位为 {@code PENDING}（推进尝试次数、锚点置当前时刻）；
     * 余额耗尽则置 {@code FAILED} 并走中止流程（后续 {@code SKIPPED} + 执行 {@code FAILED}）。
     * <p>
     * {@code resetStale} / {@code finalizer.abort} 均带状态守卫，返回 0 说明已被其它副本处理，不重复推进。
     *
     * @return 是否实际推进（复位或中止）
     */
    private boolean recover(SceneStepRun run) {
        int current = run.getAttemptCount() == null ? 0 : run.getAttemptCount();
        int nextAttempt = current + 1;
        LocalDateTime now = LocalDateTime.now();
        if (current < Math.max(1, properties.getRetryMaxAttempts())) {
            if (stepRunMapper.resetStale(run.getId(), nextAttempt, now) == 1) {
                log.info("场景卡死步骤已复位重试（第 {} 次）: stepRunId={}", nextAttempt, run.getId());
                return true;
            }
            return false;
        }
        if (stepRunMapper.claimPending(run.getId()) != 1) {
            // 已被其它副本处理或状态已变化，不再中止
            return false;
        }
        finalizer.abort(run, nextAttempt, "执行超时");
        return true;
    }

    /** 只投递不执行：巡检线程不阻塞；队列满时记录并保留到期锚点，下一轮巡检再次投递。 */
    private void deliver(Long stepRunId) {
        try {
            sceneExecutor.execute(() -> sceneActionExecutor.executeStep(stepRunId));
        } catch (RejectedExecutionException e) {
            log.warn("场景到期步骤投递被拒绝（执行队列已满），留待下轮: stepRunId={}", stepRunId);
        }
    }

    private int batchSize() {
        return Math.max(1, properties.getSweepBatchSize());
    }
}