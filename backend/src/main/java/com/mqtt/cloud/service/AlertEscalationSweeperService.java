package com.mqtt.cloud.service;

/**
 * 告警升级巡检服务（T-27 设计文档 §5.1 / §8.3）。
 * <p>
 * 每轮拉取启用中的升级策略，对每条策略按 {@code severity} 匹配未确认（{@code TRIGGERED}）
 * 且升级基准时间已越过 {@code after_seconds} 的告警，执行一次升级：{@code escalation_level + 1}、
 * 写 {@code last_escalated_at}、按策略写 {@code assignee_id}，并投递 {@code alert.escalated}。
 * 级别达到 {@code max-escalation-level} 后只刷新锚点、不再通知。
 * <p>
 * 由 {@code config.AlertEscalationSweeper} 按固定间隔调用 {@link #sweep()}；实现需自行捕获异常，
 * 不得中断调度线程。
 */
public interface AlertEscalationSweeperService {

    /** 执行一轮升级巡检。 */
    void sweep();
}