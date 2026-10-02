package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.entity.Device;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警评估服务（T-17 设计文档 §8.2 / §8.5）。
 * <p>
 * 承载评估核心四步（查活动告警 → 新建 / 去重 / 再通知 → 恢复）与抑制窗口计算，
 * 三类来源共用同一套状态流转：阈值与事件由解析链路「推」入（{@link #onProperties} /
 * {@link #onEvents}），离线由巡检「拉」入（{@link #evaluateOffline}，由
 * {@link AlertSweeperService} 逐条调用，巡检只负责挑出 (规则, 设备) 组合）。
 * <p>
 * 全程 try/catch 隔离，异常只记 WARN，绝不冒泡到解析 / 巡检链路。
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface AlertEvaluationService {

    /**
     * 阈值评估（**推**）：属性上报解析成功后调用，逐样本即时比较，越限触发、回落恢复。
     *
     * @param deviceId  设备ID
     * @param productId 产品ID（保留契约，当前按设备归属取规则，不使用产品维度）
     * @param samples   本批成功落库的属性样本
     */
    void onProperties(Long deviceId, Long productId, List<PropertySample> samples);

    /**
     * 事件评估（**推**）：事件记录落库成功后调用，命中规则即触发；事件类无自动恢复。
     *
     * @param deviceId 设备ID
     * @param samples  本批成功落库的事件样本
     */
    void onEvents(Long deviceId, List<EventSample> samples);

    /**
     * 离线评估（**拉**）：对巡检挑出的单个 (规则, 设备) 走评估核心，去重 / 新建。
     */
    void evaluateOffline(AlertRule rule, Device device);

    /**
     * 置恢复并通知：命中恢复条件（阈值回落 / 设备上线 / 规则删除）且有活动告警时调用。
     * 内部用条件更新兜底，记录已被并发置恢复时静默返回。
     */
    void recover(AlertRecord record, Device device);

    /** 属性样本：解析链路归一化后的标识符、文本值与上报时间。 */
    record PropertySample(String identifier, String valueText, LocalDateTime reportedAt) {
    }

    /** 事件样本：解析链路落库后的事件标识符、事件类型、输出与上报时间。 */
    record EventSample(String identifier, String eventType, String outputData, LocalDateTime reportedAt) {
    }
}