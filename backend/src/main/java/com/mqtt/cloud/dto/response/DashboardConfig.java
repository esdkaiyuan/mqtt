package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 看板配置模型（T-21 设计文档 §5.3）。
 * <p>
 * 与 {@code dashboard.config} 的 JSON 一一对应，仅在此处耦合 Jackson；实体 {@code Dashboard}
 * 始终以原始文本承载，落在服务层解析 / 序列化。校验（面板数、桶 / 图型 / 聚合白名单、跨度）见
 * {@code DashboardServiceImpl}。
 */
@Data
public class DashboardConfig {

    private List<Panel> panels = new ArrayList<>();

    /** 单个面板：一条「设备组 × 属性」的时序展示配置。 */
    @Data
    public static class Panel {

        /** 面板标题。 */
        private String title;

        /** 参与面板的设备 ID 列表（非空）。 */
        private List<Long> deviceIds = new ArrayList<>();

        /** 属性标识符（非空）。 */
        private String identifier;

        /** 时间桶，白名单同 {@code 1m / 5m / 15m / 30m / 1h / 6h / 1d}。 */
        private String bucket;

        /** 默认展示跨度（天），≤ {@code app.property-history.max-range-days}。 */
        private Integer rangeDays;

        /** 图型：{@code line} / {@code bar}。 */
        private String chartType;

        /** 聚合口径：{@code avg} / {@code min} / {@code max} / {@code count}。 */
        private String aggregation;
    }
}
