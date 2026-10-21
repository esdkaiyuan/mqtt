package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 看板分享创建 / 更新请求（T-46）。
 */
@Data
public class DashboardShareRequest {

    /** 分享名称（大屏标题），必填，≤ 64 字符。 */
    private String name;

    /** 看板 ID 列表，1..max-share-dashboards，按轮播顺序。 */
    private List<Long> dashboardIds;

    /** 轮播间隔（秒），缺省 15，取值由 app.dashboard.share-interval-* 约束。 */
    private Integer intervalSeconds;

    /** 是否启用，缺省 true。 */
    private Boolean enabled;
}
