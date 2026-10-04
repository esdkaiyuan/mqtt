package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import com.mqtt.cloud.service.SceneActionExecutor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * 场景执行启动器（T-23 设计文档 §8.4 / §8.5）。
 * <p>
 * 触发命中后由评估 / 定时巡检 / 手动执行三方共用的**唯一落库入口**：在同一事务内插入
 * {@code scene_execution}（{@code PENDING}）与该次执行的每个启用步骤明细
 * {@code scene_step_run}（首步按 {@code now + delay_1} 排期，其余置未排期），
 * 再投递首步到 {@code sceneExecutor}（延时 0 时）。
 * <p>
 * 投递放在事务**提交后**执行（{@link TransactionSynchronization#afterCommit()}），避免异步线程读到未提交行；
 * 队列满时把该步置 {@code FAILED} 并中止整条执行（原因「执行队列已满」）。
 * <p>
 * 落库失败宁可丢一次触发，也不影响摄取链路：异常由调用方 {@code try/catch} 兜底（只记 WARN）。
 */
@Slf4j
@Component
public class SceneExecutionLauncher {

    private static final int TRIGGER_VALUE_MAX = 512;

    private final SceneStepMapper stepMapper;
    private final SceneStepRunMapper stepRunMapper;
    private final SceneExecutionMapper executionMapper;
    private final SceneDefinitionMapper definitionMapper;
    private final SceneActionExecutor actionExecutor;
    private final SceneExecutionFinalizer finalizer;
    private final ThreadPoolTaskExecutor sceneExecutor;
    private final Counter rejectedCounter;

    public SceneExecutionLauncher(SceneStepMapper stepMapper,
                                  SceneStepRunMapper stepRunMapper,
                                  SceneExecutionMapper executionMapper,
                                  SceneDefinitionMapper definitionMapper,
                                  SceneActionExecutor actionExecutor,
                                  SceneExecutionFinalizer finalizer,
                                  @Qualifier("sceneExecutor") ThreadPoolTaskExecutor sceneExecutor,
                                  MeterRegistry meterRegistry) {
        this.stepMapper = stepMapper;
        this.stepRunMapper = stepRunMapper;
        this.executionMapper = executionMapper;
        this.definitionMapper = definitionMapper;
        this.actionExecutor = actionExecutor;
        this.finalizer = finalizer;
        this.sceneExecutor = sceneExecutor;
        this.rejectedCounter = Counter.builder("scene_rejected_total")
                .description("场景步骤投递被线程池拒绝次数")
                .register(meterRegistry);
    }

    /**
     * 落执行记录 + 初始化步骤流，并投递首步（延时 0 时）。
     *
     * @param scene        命中的场景定义
     * @param context      触发上下文快照
     * @param triggerSource 触发方式（{@code AUTO} / {@code MANUAL}）
     * @return 执行记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long launch(SceneDefinition scene, TriggerContext context, String triggerSource) {
        List<SceneStep> steps = stepMapper.selectBySceneId(scene.getId()).stream()
                .filter(step -> step.getEnabled() == null || step.getEnabled() == 1)
                .toList();

        SceneExecution execution = buildExecution(scene, context, triggerSource, steps.size());
        executionMapper.insert(execution);
        touchLastTriggered(scene.getId());

        if (steps.isEmpty()) {
            executionMapper.markSuccess(execution.getId(), LocalDateTime.now());
            return execution.getId();
        }

        LocalDateTime now = LocalDateTime.now();
        Long firstRunId = null;
        int firstDelay = 0;
        boolean first = true;
        for (SceneStep step : steps) {
            int delay = step.getDelaySeconds() == null ? 0 : step.getDelaySeconds();
            SceneStepRun run = new SceneStepRun();
            run.setExecutionId(execution.getId());
            run.setSceneId(scene.getId());
            run.setStepId(step.getId());
            run.setSeq(step.getSeq());
            run.setActionType(step.getActionType());
            run.setDelaySeconds(delay);
            run.setStatus(SceneConstants.STEP_PENDING);
            run.setAttemptCount(0);
            if (first) {
                LocalDateTime scheduledAt = now.plusSeconds(delay);
                run.setScheduledAt(scheduledAt);
                run.setNextAttemptAt(scheduledAt);
                first = false;
                firstDelay = delay;
            } else {
                run.setScheduledAt(null);
                run.setNextAttemptAt(null);
            }
            stepRunMapper.insert(run);
            if (firstRunId == null) {
                firstRunId = run.getId();
            }
        }

        if (firstDelay == 0 && firstRunId != null) {
            Long deliverId = firstRunId;
            afterCommit(() -> deliver(deliverId));
        }
        return execution.getId();
    }

    private SceneExecution buildExecution(SceneDefinition scene, TriggerContext context,
                                          String triggerSource, int totalSteps) {
        SceneExecution execution = new SceneExecution();
        execution.setUserId(scene.getUserId());
        execution.setSceneId(scene.getId());
        execution.setSceneName(scene.getName());
        execution.setTriggerType(scene.getTriggerType());
        execution.setTriggerDeviceId(context.triggerDeviceId());
        execution.setTriggerDeviceKey(context.triggerDeviceKey());
        execution.setTriggerDeviceName(context.triggerDeviceName());
        execution.setTriggerIdentifier(context.triggerIdentifier());
        execution.setTriggerValue(truncate(context.triggerValue(), TRIGGER_VALUE_MAX));
        execution.setTriggerEventType(context.triggerEventType());
        execution.setTriggerSource(triggerSource);
        execution.setTotalSteps(totalSteps);
        execution.setFinishedSteps(0);
        execution.setStatus(SceneConstants.EXEC_PENDING);
        return execution;
    }

    /** 投递步骤到场景线程池（只投递不执行）；队列满则置该步失败并中止执行。 */
    private void deliver(Long stepRunId) {
        try {
            sceneExecutor.execute(() -> actionExecutor.executeStep(stepRunId));
        } catch (RejectedExecutionException e) {
            rejectedCounter.increment();
            log.warn("场景步骤投递被拒绝（执行队列已满）: stepRunId={}", stepRunId);
            markRejected(stepRunId);
        } catch (Exception e) {
            log.warn("场景步骤投递异常: stepRunId={}", stepRunId, e);
        }
    }

    /** 队列满：抢占该步后置失败并中止整条执行。 */
    private void markRejected(Long stepRunId) {
        try {
            SceneStepRun run = stepRunMapper.selectById(stepRunId);
            if (run == null) {
                return;
            }
            if (stepRunMapper.claimPending(stepRunId) == 1) {
                finalizer.abort(run, 1, "执行队列已满");
            }
        } catch (Exception e) {
            log.warn("场景步骤拒绝收口异常: stepRunId={}", stepRunId, e);
        }
    }

    /** 回填「最近触发」展示字段（失败只记 WARN，不阻断）。 */
    private void touchLastTriggered(Long sceneId) {
        try {
            definitionMapper.touchLastTriggered(sceneId, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("场景最近触发时间回填失败: sceneId={}", sceneId, e);
        }
    }

    /** 有事务时注册提交后回调，无事务时立即执行。 */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 触发上下文快照。
     *
     * @param triggerDeviceId   触发设备 ID（TIMER 源为空）
     * @param triggerDeviceKey  触发设备标识快照
     * @param triggerDeviceName 触发设备名称快照
     * @param triggerIdentifier 触发标识符（TIMER 源为空）
     * @param triggerValue      触发值文本（TIMER 源为空）
     * @param triggerEventType  触发事件类型（非 EVENT 源为空）
     * @param triggeredAt       触发时刻
     */
    public record TriggerContext(Long triggerDeviceId,
                                 String triggerDeviceKey,
                                 String triggerDeviceName,
                                 String triggerIdentifier,
                                 String triggerValue,
                                 String triggerEventType,
                                 LocalDateTime triggeredAt) {

        /** 定时 / 手动触发：无触发设备与样本上下文。 */
        public static TriggerContext empty() {
            return new TriggerContext(null, null, null, null, null, null, LocalDateTime.now());
        }
    }
}