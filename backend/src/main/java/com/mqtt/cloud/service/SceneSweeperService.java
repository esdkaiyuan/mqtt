package com.mqtt.cloud.service;

/**
 * 场景步骤巡检服务（T-23 设计文档 §8.9）。
 * <p>
 * 延时与重试锚点（{@code next_attempt_at} / {@code attempt_count}）落在库中，本服务按固定间隔
 * **拉**取到期步骤投递执行：天然支持多副本（任一副本巡检即可）与进程重启恢复（不依赖进程内定时器）。
 * <p>
 * 三个子任务（到期拾取 / 卡死恢复 / 保留清理）各自隔离异常，由 {@code config.SceneSweeper} 调用，
 * 实现须自行捕获异常、**不得中断调度线程**。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface SceneSweeperService {

    /**
     * 拾取到期步骤：只拾取 {@code status='PENDING' AND next_attempt_at IS NOT NULL AND next_attempt_at <= now}，
     * 未排期（{@code next_attempt_at IS NULL}）的后续步骤不被拾取；逐条投递 {@code sceneExecutor}（只投递不执行）。
     *
     * @return 本轮投递条数
     */
    int sweepDueSteps();

    /**
     * 恢复卡死步骤：把长期 {@code RUNNING}（进程中途退出或动作卡死）的步骤复位为 {@code PENDING} 重试；
     * 尝试次数已耗尽则置 {@code FAILED} 并走中止流程（失败步骤 + 后续 {@code SKIPPED} + 执行 {@code FAILED}）。
     *
     * @return 本轮恢复条数
     */
    int recoverStaleSteps();

    /**
     * 保留策略清理：{@code execution-retention-days > 0} 时按批物理删除终态执行记录
     * （{@code PENDING} / {@code RUNNING} 永不清理），步骤明细随级联删除。
     *
     * @return 本轮清理条数
     */
    int purgeExpired();
}