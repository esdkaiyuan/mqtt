package com.mqtt.cloud.service;

/**
 * 规则重试巡检服务（T-19 设计文档 §8.6）。
 * <p>
 * 动作失败的重试锚点（{@code attempt_count} / {@code next_attempt_at}）落在库中，本服务按固定间隔
 * **拉**取到期记录重放：天然支持多副本（任一副本巡检即可）与进程重启恢复（不依赖进程内状态）。
 * 逐条隔离异常，由 {@code config.RuleSweeper} 调用，实现需自行捕获异常、不得中断调度线程。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface RuleSweeperService {

    /**
     * 重放到期重试：只拾取 {@code status='PENDING' AND next_attempt_at IS NOT NULL AND next_attempt_at <= now}，
     * 首次执行记录（{@code next_attempt_at IS NULL}）不被拾取，避免与线程池内的首次执行并发重复。
     *
     * @return 本轮重放条数
     */
    int sweepRetries();

    /**
     * 保留策略清理：{@code execution-retention-days > 0} 时按批物理删除终态记录
     * （{@code PENDING} 永不清理），避免丢重试。
     *
     * @return 本轮清理条数
     */
    int purgeExpired();
}