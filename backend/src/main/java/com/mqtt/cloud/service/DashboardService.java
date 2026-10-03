package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.DashboardSaveDTO;
import com.mqtt.cloud.dto.response.DashboardDetailVO;
import com.mqtt.cloud.dto.response.DashboardSummaryVO;

import java.util.List;

/**
 * 可保存看板服务（T-21 设计文档 §6.2 / §7.1）。
 * <p>
 * 看板**仅归属创建者本人**：{@code user_id} 一律由服务端从登录态注入，忽略客户端传值；
 * 非本人（或不存在）一律 {@code 6228}，不区分「不存在」与「无权限」，避免探测。
 * <p>
 * {@code config} 为客户端 JSON：服务层解析为强类型 {@code DashboardConfig} 后逐字段白名单校验
 * （面板数 / 设备集合 / 属性 / 桶 / 图型 / 聚合 / 跨度），非法 → {@code 6229}；落库为参数化 JSON。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface DashboardService {

    /**
     * 当前用户的看板列表（按更新时间倒序）。
     * <p>
     * 列表项**不含** {@code config} 明细，仅给出面板数，避免列表接口搬运大字段。
     */
    List<DashboardSummaryVO> list(Long userId);

    /**
     * 新建看板：数量超上限 → {@code 6230}；名称 / 配置非法 → {@code 6229}。
     *
     * @return 详情（{@code config} 已解析为结构化模型）
     */
    DashboardDetailVO create(Long userId, DashboardSaveDTO dto);

    /**
     * 看板详情。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 不存在或非本人 → {@code 6228}
     */
    DashboardDetailVO detail(Long userId, Long id);

    /**
     * 更新看板（名称 / 配置全量覆盖）。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 不存在或非本人 → {@code 6228}；配置非法 → {@code 6229}
     */
    DashboardDetailVO update(Long userId, Long id, DashboardSaveDTO dto);

    /**
     * 删除看板（硬删除）。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 不存在或非本人 → {@code 6228}
     */
    void remove(Long userId, Long id);
}
