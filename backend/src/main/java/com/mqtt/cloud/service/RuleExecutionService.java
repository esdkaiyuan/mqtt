package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.ExecutionQueryDTO;
import com.mqtt.cloud.entity.RuleExecution;

/**
 * 规则执行记录服务（T-19 设计文档 §10.1）。
 * <p>
 * 执行记录只增不删（终态由 {@link RuleSweeperService} 按保留策略物理清理），本服务提供分页查询、
 * 详情与手动重试。记录严格按 {@code user_id} 隔离，跨用户访问返回 {@code 403}。
 * <p>
 * 手动重试仅允许终态 {@code FAILED}：重置为 {@code PENDING} 并立即投递 {@code ruleExecutor}；
 * {@code PENDING} / {@code SUCCESS} 记录重试返回 {@code 409} + {@code RULE_INVALID}。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface RuleExecutionService {

    /**
     * 执行记录分页（本人），可按规则 / 设备 / 状态过滤，按创建时间倒序。
     */
    IPage<RuleExecution> page(Long userId, ExecutionQueryDTO query);

    /**
     * 取本人名下的执行记录：不存在抛 {@code 6221}，越权抛 {@code 403}。
     */
    RuleExecution getOwned(Long userId, Long id);

    /**
     * 手动重试：仅允许 {@code FAILED} 记录，重置为 {@code PENDING} 并立即投递执行。
     * <p>
     * 非 {@code FAILED} 或已被其它副本处理（条件更新影响 0 行）抛 {@code 409} + {@code RULE_INVALID}。
     */
    void manualRetry(Long userId, Long id);
}