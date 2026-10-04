package com.mqtt.cloud.service;

/**
 * 场景步骤动作执行器（T-23 设计文档 §8.6 / §8.7）。
 * <p>
 * 执行单个步骤明细（{@code scene_step_run.id}）：以 {@code claimPending} 为唯一抢占入口，
 * 分派 {@code UPDATE_PROPERTY} / {@code SEND_COMMAND} / {@code FORWARD_MQTT} / {@code FORWARD_HTTP}
 * 四类动作，成功后推进执行进度并排期下一步；失败按 §8.8 退避重试或中止后续。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface SceneActionExecutor {

    /**
     * 执行单个步骤；并发保护由 {@code claimPending} 条件更新兜底，未抢到直接返回。
     * <p>
     * 实现须自行捕获异常并转为重试 / 终态，**绝不冒泡**到线程池或巡检线程。
     */
    void executeStep(Long stepRunId);
}