package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.AlertEscalationPolicyRequest;
import com.mqtt.cloud.entity.AlertEscalationPolicy;

import java.util.List;

/**
 * 告警升级策略服务（T-27 设计文档 §6.4 / §7 / §10.1）。
 * <p>
 * 策略按 {@code user_id} 隔离，读写前先做归属校验；参数校验矩阵见设计 §7，
 * 命中即抛 {@code 6266}。{@code channel_ids} 中的渠道须为本人所有，防止跨租户引用。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**，
 * 持久化细节只在 {@code service.impl} 中处理。
 */
public interface AlertEscalationService extends IService<AlertEscalationPolicy> {

    /** 列出本人升级策略。 */
    List<AlertEscalationPolicy> listByUser(Long userId);

    /** 取本人升级策略；不存在抛 {@code 6265}，非本人抛 {@code 403}。 */
    AlertEscalationPolicy getOwned(Long userId, Long id);

    /** 新建升级策略：校验矩阵见设计 §7，命中即抛 {@code 6266}。 */
    AlertEscalationPolicy create(Long userId, AlertEscalationPolicyRequest request);

    /** 更新升级策略：校验同创建；不存在抛 {@code 6265}。 */
    AlertEscalationPolicy update(Long userId, Long id, AlertEscalationPolicyRequest request);

    /** 删除升级策略（逻辑删除）；不存在抛 {@code 6265}，非本人抛 {@code 403}。 */
    void delete(Long userId, Long id);

    /** 取启用中的升级策略（供升级巡检消费），按 ID 升序取前 {@code limit} 条。 */
    List<AlertEscalationPolicy> listEnabled(int limit);
}