package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.SceneQuery;
import com.mqtt.cloud.dto.request.SceneRequest;
import com.mqtt.cloud.entity.SceneDefinition;

/**
 * 场景联动服务（T-23 设计文档 §7 / §10.1）。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**，
 * 持久化细节只在 {@code service.impl} 中处理。
 * <p>
 * 校验：非法配置统一抛 {@code 6241}，非法动作类型抛 {@code 6242}，非法触发源抛 {@code 6245}，
 * 超上限抛 {@code 6244}，越权设备抛 {@code 2003}，场景不存在抛 {@code 6240}，资源越权抛 {@code 403}。
 */
public interface SceneService {

    /**
     * 场景分页列表（本人），可按触发源 / 启用态 / 关键字过滤。
     * <p>
     * 列表返回场景定义字段与已解析条件组；步骤流仅在详情接口返回（避免列表 N+1 查询）。
     */
    IPage<SceneDefinition> listScenes(Long userId, SceneQuery query);

    /**
     * 取本人名下的场景详情（含条件组与步骤流）：不存在抛 {@code 6240}，越权抛 {@code 403}。
     */
    SceneDefinition getScene(Long userId, Long id);

    /**
     * 创建场景：完整校验（名称 / 触发源 / 条件组 / 步骤流 / 目标 / 动作配置）后落库，
     * 步骤在同一事务内插入；重名 / 非法配置抛 {@code 6241}。
     */
    SceneDefinition createScene(Long userId, SceneRequest request);

    /**
     * 更新场景：**步骤整体替换**（先删旧步骤再插新步骤，同一事务）。
     */
    SceneDefinition updateScene(Long userId, Long id, SceneRequest request);

    /**
     * 删除场景：逻辑删除；执行记录保留。
     */
    void deleteScene(Long userId, Long id);

    /**
     * 启用 / 停用场景。
     */
    void setEnabled(Long userId, Long id, boolean enabled);

    /**
     * 手动执行一次（真实触发，{@code trigger_source=MANUAL}，不占用冷却锚点、不做触发条件判定）。
     * <p>
     * 场景须属当前用户且**至少有一个启用步骤**，否则抛 {@code 6240} / {@code 6241}。
     *
     * @return 新建的执行记录 ID
     */
    Long runManually(Long userId, Long id);

    /**
     * 主动失效本副本的场景定义缓存（T-23 设计文档 §8.2）。
     * <p>
     * 保存 / 启停 / 删除后调用；多副本下其余副本的收敛窗口为 {@code app.scene.cache-ttl-seconds}。
     */
    void evictCache(Long userId);
}