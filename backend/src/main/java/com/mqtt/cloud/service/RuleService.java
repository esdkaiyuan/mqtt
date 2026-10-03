package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.RuleQueryDTO;
import com.mqtt.cloud.dto.request.RuleRequest;
import com.mqtt.cloud.dto.request.RuleTestRequest;
import com.mqtt.cloud.dto.response.RuleTestResult;
import com.mqtt.cloud.entity.RuleDefinition;

/**
 * 消息规则服务（T-19 设计文档 §7 / §10.1）。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**，
 * 持久化细节只在 {@code service.impl} 中处理。
 */
public interface RuleService {

    /**
     * 规则分页列表（本人），可按来源 / 动作 / 启用态 / 关键字过滤。
     */
    IPage<RuleDefinition> page(Long userId, RuleQueryDTO query);

    /**
     * 创建规则：完整校验（名称 / 来源 / 条件 / 动作），重名与非法配置抛 {@code 6219}，
     * 非法动作类型抛 {@code 6220}，越权设备抛 {@code 2003}，超上限抛 {@code 6222}。
     */
    RuleDefinition create(Long userId, RuleRequest request);

    /**
     * 更新规则：不存在抛 {@code 6218}，越权抛 {@code 403}，校验同创建。
     */
    RuleDefinition update(Long userId, Long id, RuleRequest request);

    /**
     * 取本人名下的规则：不存在抛 {@code 6218}，越权抛 {@code 403}。
     */
    RuleDefinition getOwned(Long userId, Long id);

    /**
     * 删除规则：逻辑删除，执行记录保留。
     */
    void delete(Long userId, Long id);

    /**
     * 启用 / 停用规则。
     */
    void setEnabled(Long userId, Long id, boolean enabled);

    /**
     * 试运行（干跑）：用给定设备 / 标识符 / 取值判定条件是否命中，返回命中结论 + 诊断，
     * **不产生任何副作用**（不落执行记录、不发送、不更新冷却锚点）。
     */
    RuleTestResult test(Long userId, Long id, RuleTestRequest request);

    /**
     * 主动失效本副本的规则缓存（T-19 设计文档 §8.2）。
     * <p>
     * 保存 / 启停 / 删除后调用；多副本下其余副本的收敛窗口为 {@code app.rule.cache-ttl-seconds}。
     */
    void evictCache(Long userId);
}