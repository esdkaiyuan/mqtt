package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.PointTableUpsertRequest;
import com.mqtt.cloud.dto.response.PointTableDTO;
import com.mqtt.cloud.entity.DevicePointTable;

import java.util.List;

/**
 * 设备点位表服务（T-31）
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 * <p>
 * <b>核心职责是守住「执行方隔离」</b>：同一台设备下的点位必须统一由一条路线采集，
 * 混用会导致同一点位被双写，既污染最新值又在属性历史里留下同一时刻的重复点。
 * 该约束由 {@link #savePoints} 在写入时校验并拒绝，不做静默改写——
 * 静默改写会让人以为切换成功，实际却在下次写入时被覆盖，问题暴露得更晚。
 */
public interface DevicePointTableService {

    /**
     * 批量保存设备点位（新增与更新合并，按 {@code pointKey} 判定）。
     * <p>
     * 校验顺序：单项合法性 → 整批非空 → <b>执行方一致性</b> → 落库。
     * <p>
     * 执行方一致性的判定范围是「该设备已有点位的执行方」∪「本批点位的执行方」，
     * 若结果不止一种则拒绝整批写入 —— 整批拒绝而非部分成功，是为了让调用方能明确知道
     * 「这一批没有生效」，而不是拿到一个半成功的结果去排查数据不一致。
     *
     * @param ownerId 操作人（用于设备归属校验，平台侧任务可为 {@code null} 表示跳过）
     * @param deviceId 设备ID
     * @param request 点位列表
     * @return 实际保存的点数
     */
    int savePoints(Long ownerId, Long deviceId, List<PointTableUpsertRequest> request);

    /** 按设备取点位；{@code executor} 为空表示不区分执行方，否则按执行方过滤。 */
    List<PointTableDTO> listPoints(Long ownerId, Long deviceId, String executor);

    /** 删除单个点位（同时清理其采集日志，避免孤儿日志持续增长）。 */
    void deletePoint(Long ownerId, Long deviceId, Long pointId);

    /** 取设备下启用且指定执行方的点位实体，供采集 SPI 使用（内部调用，不做归属校验）。 */
    List<DevicePointTable> listEnabledForCollect(Long deviceId, String executor);

    /** 该设备点表当前使用的执行方；无点位时返回 {@code null}。 */
    String resolveExecutor(Long deviceId);
}
