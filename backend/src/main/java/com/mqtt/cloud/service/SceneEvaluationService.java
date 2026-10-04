package com.mqtt.cloud.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景触发评估服务（T-23 设计文档 §8.1 / §8.2）。
 * <p>
 * 触发判定以 {@code ThingModelInterpretService} 内的**第四条内部旁路**形式承载（不新增
 * {@code IngestDispatcher} fan-out 路），复用 T-14 已归一化的属性 / 事件样本；与 T-16 影子、
 * T-17 告警、T-19 规则并列，但**不依赖告警 / 规则域的类型**，避免场景引擎反向耦合。
 * <p>
 * 命中后按 §8.4 冷却节流 → §8.5 落执行记录并初始化步骤流（唯一一次落库事务）→ 投递独立线程池
 * {@code sceneExecutor} 异步执行。全程 {@code try/catch} 隔离：异常只记 WARN，**绝不冒泡**到解析链路。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface SceneEvaluationService {

    /**
     * 属性场景评估（**推**）：属性上报解析成功后调用，逐样本内存匹配，命中即触发。
     *
     * @param deviceId  设备ID
     * @param productId 产品ID（保留契约，当前按设备归属取场景，不使用产品维度）
     * @param samples   本批成功落库的属性样本
     */
    void onProperties(Long deviceId, Long productId, List<PropertySample> samples);

    /**
     * 事件场景评估（**推**）：事件记录落库成功后调用，命中即触发。
     *
     * @param deviceId 设备ID
     * @param samples  本批成功落库的事件样本
     */
    void onEvents(Long deviceId, List<EventSample> samples);

    /**
     * 主动失效本副本的场景定义缓存（写操作后由 {@link SceneService} 调用）。
     *
     * @param userId 归属用户；{@code null} 时忽略
     */
    void evictCache(Long userId);

    /** 属性样本：解析链路归一化后的标识符、文本值与上报时间。 */
    record PropertySample(String identifier, String valueText, LocalDateTime reportedAt) {
    }

    /** 事件样本：解析链路落库后的事件标识符、事件类型、输出与上报时间。 */
    record EventSample(String identifier, String eventType, String outputData, LocalDateTime reportedAt) {
    }
}