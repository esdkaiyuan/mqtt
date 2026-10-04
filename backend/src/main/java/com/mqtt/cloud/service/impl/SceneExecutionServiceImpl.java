package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.SceneExecutionQuery;
import com.mqtt.cloud.dto.response.SceneExecutionDetail;
import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import com.mqtt.cloud.service.SceneActionExecutor;
import com.mqtt.cloud.service.SceneExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * 场景执行记录服务实现（T-23 设计文档 §5.5 / §10.1）。
 * <p>
 * 记录按 {@code user_id} 隔离（越权 {@code 403}、不存在 {@code 6243}）。手动重试仅允许终态 {@code FAILED}：
 * 条件更新 {@code executionMapper.resetForRetry}（{@code status='FAILED'} 守卫）置回 {@code RUNNING} 后，
 * 把失败 / 已跳过步骤重置为 {@code PENDING}（仅最小序号步骤立即排期，其余待前序成功后逐级排期），
 * 并回退 {@code finished_steps} 中已跳过的计数；投递放在事务提交后执行，避免异步线程读到未提交行。
 * <p>
 * 状态冲突复用 {@code SCENE_INVALID} 业务码但返回 {@code 409}，不新增同义错误码。
 */
@Slf4j
@Service
public class SceneExecutionServiceImpl implements SceneExecutionService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final SceneExecutionMapper executionMapper;
    private final SceneStepRunMapper stepRunMapper;
    private final SceneActionExecutor sceneActionExecutor;
    private final ThreadPoolTaskExecutor sceneExecutor;

    public SceneExecutionServiceImpl(SceneExecutionMapper executionMapper,
                                     SceneStepRunMapper stepRunMapper,
                                     SceneActionExecutor sceneActionExecutor,
                                     @Qualifier("sceneExecutor") ThreadPoolTaskExecutor sceneExecutor) {
        this.executionMapper = executionMapper;
        this.stepRunMapper = stepRunMapper;
        this.sceneActionExecutor = sceneActionExecutor;
        this.sceneExecutor = sceneExecutor;
    }

    @Override
    public IPage<SceneExecution> page(Long userId, SceneExecutionQuery query) {
        SceneExecutionQuery effective = query == null ? new SceneExecutionQuery() : query;
        int pageNum = (effective.getPageNum() == null || effective.getPageNum() < 1)
                ? 1 : effective.getPageNum();
        int pageSize = (effective.getPageSize() == null || effective.getPageSize() < 1)
                ? DEFAULT_PAGE_SIZE : Math.min(effective.getPageSize(), MAX_PAGE_SIZE);
        return executionMapper.pageByUser(new Page<>(pageNum, pageSize), userId,
                effective.getSceneId(), effective.getDeviceId(), normalizeUpper(effective.getStatus()));
    }

    @Override
    public SceneExecutionDetail getDetail(Long userId, Long id) {
        SceneExecution execution = getOwned(userId, id);
        List<SceneStepRun> steps = stepRunMapper.selectByExecutionId(id);
        return new SceneExecutionDetail(execution, steps == null ? List.of() : steps);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualRetry(Long userId, Long id) {
        SceneExecution execution = getOwned(userId, id);
        if (!SceneConstants.EXEC_FAILED.equals(execution.getStatus())) {
            throw conflict("仅失败（FAILED）记录可手动重试，当前状态 " + execution.getStatus());
        }
        List<SceneStepRun> steps = stepRunMapper.selectByExecutionId(id);
        Integer firstSeq = null;
        Long firstStepRunId = null;
        int skipped = 0;
        for (SceneStepRun step : steps) {
            boolean replayable = SceneConstants.STEP_FAILED.equals(step.getStatus())
                    || SceneConstants.STEP_SKIPPED.equals(step.getStatus());
            if (SceneConstants.STEP_SKIPPED.equals(step.getStatus())) {
                skipped++;
            }
            if (replayable && step.getSeq() != null && (firstSeq == null || step.getSeq() < firstSeq)) {
                firstSeq = step.getSeq();
                firstStepRunId = step.getId();
            }
        }
        if (firstSeq == null) {
            throw conflict("无可重试的失败步骤");
        }
        if (executionMapper.resetForRetry(id) == 0) {
            throw conflict("执行记录状态已变更，请刷新后重试");
        }
        stepRunMapper.resetForRetry(id, firstSeq, LocalDateTime.now());
        if (skipped > 0) {
            executionMapper.bumpFinishedSteps(id, -skipped);
        }
        Long deliverId = firstStepRunId;
        if (deliverId != null) {
            afterCommit(() -> dispatch(deliverId));
        }
    }

    private SceneExecution getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.SCENE_EXECUTION_NOT_FOUND);
        }
        SceneExecution execution = executionMapper.selectById(id);
        if (execution == null) {
            throw new BusinessException(ResultCode.SCENE_EXECUTION_NOT_FOUND);
        }
        if (!userId.equals(execution.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return execution;
    }

    /** 只投递不执行；队列满时保留到期锚点（{@code next_attempt_at=now}），由 {@code SceneSweeper} 下轮拾取。 */
    private void dispatch(Long stepRunId) {
        try {
            sceneExecutor.execute(() -> sceneActionExecutor.executeStep(stepRunId));
        } catch (RejectedExecutionException e) {
            log.warn("场景手动重试投递被拒绝（执行队列已满），留待巡检拾取: stepRunId={}", stepRunId);
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

    private static BusinessException conflict(String message) {
        return new BusinessException(ResultCode.SCENE_INVALID, HttpStatus.CONFLICT, message);
    }

    private static String normalizeUpper(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }
}