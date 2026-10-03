package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.ExecutionQueryDTO;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.mapper.RuleExecutionMapper;
import com.mqtt.cloud.service.RuleActionExecutor;
import com.mqtt.cloud.service.RuleExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

/**
 * 规则执行记录服务实现（T-19 设计文档 §10.1）。
 * <p>
 * 记录按 {@code user_id} 隔离（越权 {@code 403}、不存在 {@code 6221}）。手动重试仅允许终态
 * {@code FAILED}：条件更新 {@code resetForRetry}（{@code status='FAILED'} 守卫）重置为
 * {@code PENDING} 后立即投递 {@code ruleExecutor}，与首次执行共用
 * {@link RuleActionExecutor#execute(Long)} 入口。
 * <p>
 * 状态冲突复用 {@code RULE_INVALID} 业务码但返回 {@code 409}，不新增同义错误码。
 */
@Slf4j
@Service
public class RuleExecutionServiceImpl implements RuleExecutionService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final RuleExecutionMapper executionMapper;
    private final RuleActionExecutor ruleActionExecutor;
    private final ThreadPoolTaskExecutor ruleExecutor;

    public RuleExecutionServiceImpl(RuleExecutionMapper executionMapper,
                                    RuleActionExecutor ruleActionExecutor,
                                    @Qualifier("ruleExecutor") ThreadPoolTaskExecutor ruleExecutor) {
        this.executionMapper = executionMapper;
        this.ruleActionExecutor = ruleActionExecutor;
        this.ruleExecutor = ruleExecutor;
    }

    @Override
    public IPage<RuleExecution> page(Long userId, ExecutionQueryDTO query) {
        ExecutionQueryDTO effective = query == null ? new ExecutionQueryDTO() : query;
        int pageNum = (effective.getPageNum() == null || effective.getPageNum() < 1)
                ? 1 : effective.getPageNum();
        int pageSize = (effective.getPageSize() == null || effective.getPageSize() < 1)
                ? DEFAULT_PAGE_SIZE : Math.min(effective.getPageSize(), MAX_PAGE_SIZE);
        return executionMapper.pageByUser(new Page<>(pageNum, pageSize), userId,
                effective.getRuleId(), effective.getDeviceId(), normalizeUpper(effective.getStatus()));
    }

    @Override
    public RuleExecution getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.RULE_EXECUTION_NOT_FOUND);
        }
        RuleExecution execution = executionMapper.selectById(id);
        if (execution == null) {
            throw new BusinessException(ResultCode.RULE_EXECUTION_NOT_FOUND);
        }
        if (!userId.equals(execution.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return execution;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualRetry(Long userId, Long id) {
        RuleExecution execution = getOwned(userId, id);
        if (!RuleConstants.STATUS_FAILED.equals(execution.getStatus())) {
            throw conflict("仅失败（FAILED）记录可手动重试，当前状态 " + execution.getStatus());
        }
        int updated = executionMapper.resetForRetry(id);
        if (updated == 0) {
            throw conflict("执行记录状态已变更，请刷新后重试");
        }
        dispatch(id);
    }

    /** 投递异步执行；队列满时置回 FAILED，避免记录卡在 PENDING 且无重试锚点。 */
    private void dispatch(Long executionId) {
        try {
            ruleExecutor.execute(() -> ruleActionExecutor.execute(executionId));
        } catch (RejectedExecutionException e) {
            log.warn("规则手动重试投递被拒绝（执行队列已满）: executionId={}", executionId);
            executionMapper.markFailed(executionId, 0, "执行队列已满", LocalDateTime.now());
        }
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ResultCode.RULE_INVALID, HttpStatus.CONFLICT, message);
    }

    private static String normalizeUpper(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }
}