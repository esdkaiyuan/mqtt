package com.mqtt.cloud.service;

/**
 * 规则动作执行器（T-19 设计文档 §8.5）。
 * <p>
 * 首次执行与重试**共用同一入口**：入参只有执行记录 ID，动作所需的规则配置 / 设备 / 触发快照
 * 全部在执行时按 ID 回查，因此线程池投递、巡检重放、手动重试三条路径无需各自携带上下文。
 * <p>
 * 执行流程：回填「最近触发」展示字段 → 读取记录（非 {@code PENDING} 直接跳过）→ 按规则归属校验目标设备 →
 * 渲染载荷模板 → 按 {@code actionType} 分派四类动作 → 成功置 {@code SUCCESS}，失败按指数退避进入重试，
 * 重试耗尽置 {@code FAILED}。终态与重试锚点更新均带 {@code status='PENDING'} 守卫，多副本并发下幂等。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**，
 * 持久化细节只在 {@code service.impl} 中处理。
 */
public interface RuleActionExecutor {

    /**
     * 执行一条规则执行记录（首次执行与重试共用）。
     * <p>
     * 记录不存在、已终态或已被其它副本处理时静默返回；本方法不抛异常，失败原因落
     * {@code rule_execution.error_message}，由巡检与手动重试兜底。
     *
     * @param executionId 执行记录 ID
     */
    void execute(Long executionId);
}