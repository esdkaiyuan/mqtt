package com.mqtt.cloud.service;

/**
 * 通知投递巡检服务（T-27 设计文档 §8.2）。
 * <p>
 * 每轮做两件事：① 取 {@code status=FAILED} 且 {@code next_attempt_at <= now} 的投递记录重投对应渠道；
 * ② 按 {@code retention-days} 清理过期记录。重投成功置 {@code SUCCESS} 并清空重试锚点，
 * 失败按 {@code backoff-seconds × 2^(attempt_count-1)} 顺延，达 {@code max-attempts} 后置终态停止。
 * <p>
 * 由 {@code config.NotificationSweeper} 按固定间隔调用 {@link #sweep()}；实现需自行捕获异常，
 * 不得中断调度线程。
 */
public interface NotificationSweeperService {

    /** 执行一轮投递巡检：重试到期失败记录并清理过期留痕。 */
    void sweep();
}