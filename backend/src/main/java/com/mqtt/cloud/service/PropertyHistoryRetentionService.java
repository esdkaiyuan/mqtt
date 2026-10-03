package com.mqtt.cloud.service;

/**
 * 属性历史保留清理服务（T-21 设计文档 §5.4 / §7.1）。
 * <p>
 * 历史表为纯追加、无终态标记，保留策略即「硬删除早于 {@code now - retention-days} 的行」。
 * 由 {@code config.PropertyHistorySweeper} 按固定间隔调用，实现需分批删除避免长事务锁表，
 * 且自行捕获异常、不得中断调度线程。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface PropertyHistoryRetentionService {

    /**
     * 保留策略清理：{@code retention-days > 0} 时按批物理删除过期历史，直到单批影响行数不足批量。
     *
     * @return 本轮清理条数；配置非正时返回 0
     */
    int purgeExpired();
}
