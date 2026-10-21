package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开大屏分享视图（T-46）。
 * <p>
 * 只暴露启用分享中的看板配置、看板名称与设备显示名；不含用户、令牌或任何写接口。
 */
@Data
public class DashboardSharePublicVO {

    private String name;

    private Integer intervalSeconds;

    private List<DashboardItem> dashboards = new ArrayList<>();

    /** 设备 ID 文本 → 设备名，用于图表图例（仅限分享创建者名下设备）。 */
    private Map<String, String> deviceNames = new LinkedHashMap<>();

    @Data
    public static class DashboardItem {

        private Long id;

        private String name;

        private DashboardConfig config;

        private LocalDateTime updatedAt;
    }
}
