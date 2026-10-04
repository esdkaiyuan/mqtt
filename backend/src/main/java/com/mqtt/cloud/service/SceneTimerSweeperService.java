package com.mqtt.cloud.service;

/**
 * 场景定时触发巡检服务（T-23 设计文档 §8.4）。
 * <p>
 * 与 {@link SceneSweeperService}（延时 / 重试巡检）分离：本服务只负责「按分钟扫描启用中的 {@code TIMER}
 * 场景并触发」，不涉及步骤推进。定时触发的锚点（{@code last_triggered_at}）落在库中，多副本靠
 * {@code touchLastTriggeredOnce} 的**数据库级条件更新**做分钟去重，任一副本巡检即可、不重复触发。
 * <p>
 * 由 {@code config.SceneTimerSweeper} 按 {@code app.scene.sweep-interval-ms} 调用；实现须自行捕获异常、
 * **不得中断调度线程**。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface SceneTimerSweeperService {

    /**
     * 扫描启用的定时场景：cron 命中当前分钟 → 分钟去重 → 冷却判定 → 条件组判定 → 落执行记录 + 投递。
     *
     * @return 本轮实际触发条数
     */
    int sweepTimers();
}