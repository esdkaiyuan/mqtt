package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.SceneExecutionQuery;
import com.mqtt.cloud.dto.response.SceneExecutionDetail;
import com.mqtt.cloud.entity.SceneExecution;

/**
 * 场景执行记录服务（T-23 设计文档 §5.5 / §10.1）。
 * <p>
 * 执行记录只增不删（终态由 {@link SceneSweeperService} 按保留策略物理清理），本服务提供分页查询、
 * 详情与手动重试。记录严格按 {@code user_id} 隔离，跨用户访问返回 {@code 403}。
 * <p>
 * 手动重试仅允许终态 {@code FAILED}：把失败 / 已跳过步骤重置为 {@code PENDING} 并从**最小序号**的失败步骤
 * 继续（已 {@code SUCCESS} 的步骤不重放），随后立即投递；{@code PENDING} / {@code RUNNING} 记录重试返回
 * {@code 409} + {@code SCENE_INVALID}。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface SceneExecutionService {

    /**
     * 执行记录分页（本人），可按场景 / 触发设备 / 状态过滤，按创建时间倒序。
     */
    IPage<SceneExecution> page(Long userId, SceneExecutionQuery query);

    /**
     * 取本人名下的执行详情（含步骤明细）：不存在抛 {@code 6243}，越权抛 {@code 403}。
     */
    SceneExecutionDetail getDetail(Long userId, Long id);

    /**
     * 手动重试：仅允许 {@code FAILED} 记录，重置失败 / 跳过步骤并立即投递。
     * <p>
     * 非 {@code FAILED} 或已被其它副本处理（条件更新影响 0 行）抛 {@code 409} + {@code SCENE_INVALID}。
     */
    void manualRetry(Long userId, Long id);
}