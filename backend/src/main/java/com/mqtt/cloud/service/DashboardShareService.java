package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.DashboardShareRequest;
import com.mqtt.cloud.dto.response.DashboardSharePublicVO;
import com.mqtt.cloud.dto.response.DashboardShareVO;

import java.util.List;

/**
 * 看板分享与大屏公开访问（T-46）。
 * <p>
 * 创建者管理分享；公开侧仅凭令牌读取启用分享中的看板配置，无登录态、无写能力。
 * 令牌只存 SHA-256 哈希，明文仅在创建 / 轮换时返回一次。
 */
public interface DashboardShareService {

    /** 本人分享列表（按更新时间倒序，不返回明文令牌）。 */
    List<DashboardShareVO> list(Long userId);

    /** 创建分享，返回明文令牌与大屏路径（仅此一次）。 */
    DashboardShareVO create(Long userId, DashboardShareRequest request);

    /** 更新名称 / 看板集合 / 间隔 / 启停，令牌不变。 */
    DashboardShareVO update(Long userId, Long id, DashboardShareRequest request);

    /** 删除分享（立即失效）。 */
    void delete(Long userId, Long id);

    /** 轮换令牌：旧链接立即失效，返回新明文令牌。 */
    DashboardShareVO rotate(Long userId, Long id);

    /** 公开只读视图：令牌无效 / 已停用 / 看板全部不可用时返回 {@code 6305}。 */
    DashboardSharePublicVO publicView(String token);
}
