package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.AlertRuleRequest;
import com.mqtt.cloud.entity.AlertRule;

import java.util.List;

/**
 * 告警规则服务（T-17 设计文档 §7.1 / §10.1）。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**，
 * 持久化细节只在 {@code service.impl} 中处理。
 */
public interface AlertRuleService extends IService<AlertRule> {

    /**
     * 创建规则：完整校验（≤64 名称、来源/级别/比较符枚举、阈值可解析性等），
     * 设备归属校验失败抛 {@code 2003}，其余非法配置抛 {@code 6208}。
     */
    AlertRule create(Long userId, AlertRuleRequest request);

    /**
     * 更新规则：不存在 / 越权抛 {@code 6207} / {@code 403}，校验失败抛 {@code 6208}。
     */
    AlertRule update(Long userId, Long id, AlertRuleRequest request);

    /**
     * 删除规则：逻辑删除（不影响历史告警记录，活动告警由巡检「规则已删除」分支收敛）。
     */
    void delete(Long userId, Long id);

    /**
     * 规则列表（本人），可按来源类型与启用状态过滤，按创建时间倒序。
     *
     * @param sourceType 可空，空则不过滤
     * @param enabled    可空，空则不过滤
     */
    List<AlertRule> list(Long userId, String sourceType, Boolean enabled);

    /**
     * 取本人名下的规则：不存在抛 {@code 6207}，越权抛 {@code 403}。
     */
    AlertRule getOwned(Long userId, Long id);
}
