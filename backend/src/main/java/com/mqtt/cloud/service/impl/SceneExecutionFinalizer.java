package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景执行中止收口（T-23 设计文档 §8.8 / §8.9）。
 * <p>
 * 步骤重试耗尽（或卡死恢复时次数已尽）后统一走本组件：把当前步骤置 {@code FAILED}、该执行下仍为
 * {@code PENDING} 的后续步骤置 {@code SKIPPED}（并计入进度）、整条执行置 {@code FAILED}。
 * <p>
 * 供 {@code SceneActionExecutorImpl}（重试耗尽）与 {@code SceneSweeperServiceImpl}（卡死恢复次数耗尽）
 * 共用，避免中止口径在两处各自实现而产生漂移。调用前该步骤必须处于 {@code RUNNING}
 * （{@code markFailed} 的 {@code status='RUNNING'} 守卫依赖此前提）。
 */
@Slf4j
@Component
public class SceneExecutionFinalizer {

    private final SceneStepRunMapper stepRunMapper;
    private final SceneExecutionMapper executionMapper;

    public SceneExecutionFinalizer(SceneStepRunMapper stepRunMapper,
                                   SceneExecutionMapper executionMapper) {
        this.stepRunMapper = stepRunMapper;
        this.executionMapper = executionMapper;
    }

    /**
     * 中止：失败步骤 + 跳过后续 + 执行失败。全程 {@code try/catch}，异常只记 WARN。
     *
     * @param run          已抢占（{@code RUNNING}）的步骤明细
     * @param attemptCount 最终尝试次数
     * @param reason       失败原因（已截断）
     */
    public void abort(SceneStepRun run, int attemptCount, String reason) {
        LocalDateTime now = LocalDateTime.now();
        try {
            stepRunMapper.markFailed(run.getId(), attemptCount, reason, now);
            int skipped = 0;
            List<SceneStepRun> siblings = stepRunMapper.selectByExecutionId(run.getExecutionId());
            for (SceneStepRun sibling : siblings) {
                if (SceneConstants.STEP_PENDING.equals(sibling.getStatus())
                        && stepRunMapper.markSkipped(sibling.getId(), now) == 1) {
                    skipped++;
                }
            }
            if (skipped > 0) {
                executionMapper.bumpFinishedSteps(run.getExecutionId(), skipped);
            }
            executionMapper.markFailed(run.getExecutionId(),
                    "第 " + run.getSeq() + " 步失败：" + reason, now);
            log.warn("场景步骤重试耗尽，中止执行: executionId={}, seq={}, 原因={}",
                    run.getExecutionId(), run.getSeq(), reason);
        } catch (Exception e) {
            log.warn("场景执行中止收口异常: executionId={}, stepRunId={}",
                    run.getExecutionId(), run.getId(), e);
        }
    }
}