package com.mqtt.cloud.service;

/**
 * 告警离线巡检服务（T-17 设计文档 §8.3）。
 * <p>
 * 离线告警采用「拉」模式：状态改写入口多（批量摄取、单条更新、超时巡检、LWT 直离线），
 * 逐处挂钩既侵入主链路又易漏；改为周期巡检后，新建规则对「已离线设备」同样生效。
 * <p>
 * 本服务负责巡检编排（取规则、查作用域内离线设备、规则删除兜底），逐条 (规则, 设备)
 * 的评估与状态流转下沉 {@link AlertEvaluationService}。由 {@code config.AlertSweeper}
 * 按固定间隔调用 {@link #sweep()}，实现需自行捕获异常，不得中断调度线程。
 */
public interface AlertSweeperService {

    /** 执行一轮离线巡检：触发（离线超阈值）与恢复（重新上线 / 规则已删除）。 */
    void sweep();
}