package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板分享视图（T-46）。
 * <p>
 * {@code token} / {@code path} 仅在创建或轮换时返回明文；列表与更新响应只给
 * {@code tokenPreview}，避免明文令牌被反复读取。
 */
@Data
public class DashboardShareVO {

    private Long id;

    private String name;

    /** 令牌前缀（列表识别用）。 */
    private String tokenPreview;

    /** 有序看板 ID。 */
    private List<Long> dashboardIds;

    private Integer intervalSeconds;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 明文令牌：仅创建 / 轮换响应有值。 */
    private String token;

    /** 大屏访问路径：仅创建 / 轮换响应有值。 */
    private String path;
}
