package com.mqtt.cloud.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息规则评估服务（T-19 设计文档 §8.2 / §8.4）。
 * <p>
 * 条件判定以 {@code ThingModelInterpretService} 内的**内部旁路**形式承载（不新增 {@code IngestDispatcher}
 * fan-out 路），复用 T-14 已归一化的属性 / 事件样本；与 T-17 告警评估同构，但**不依赖告警包的类型**，
 * 避免规则引擎反向耦合告警域。
 * <p>
 * 命中后落一条 {@code rule_execution}（{@code PENDING}）并投递独立线程池 {@code ruleExecutor} 异步执行动作。
 * 全程 {@code try/catch} 隔离：异常只记 WARN，**绝不冒泡**到解析链路。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface RuleEvaluationService {

    /**
     * 属性规则评估（**推**）：属性上报解析成功后调用，逐样本内存匹配，命中即触发。
     *
     * @param deviceId  设备ID
     * @param productId 产品ID（保留契约，当前按设备归属取规则，不使用产品维度）
     * @param samples   本批成功落库的属性样本
     */
    void onProperties(Long deviceId, Long productId, List<PropertySample> samples);

    /**
     * 事件规则评估（**推**）：事件记录落库成功后调用，命中即触发。
     *
     * @param deviceId 设备ID
     * @param samples  本批成功落库的事件样本
     */
    void onEvents(Long deviceId, List<EventSample> samples);

    /** 主动失效本副本规则缓存（写操作后由 {@link RuleService} 调用）。 */
    void evictCache(Long userId);

    /** 属性样本：解析链路归一化后的标识符、文本值与上报时间。 */
    record PropertySample(String identifier, String valueText, LocalDateTime reportedAt) {
    }

    /** 事件样本：解析链路落库后的事件标识符、事件类型、输出与上报时间。 */
    record EventSample(String identifier, String eventType, String outputData, LocalDateTime reportedAt) {
    }
}