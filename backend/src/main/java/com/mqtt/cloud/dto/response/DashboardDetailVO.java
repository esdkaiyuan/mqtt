package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板详情（T-21 设计文档 §6.2）。
 * <p>
 * <b>只读投影，非表实体</b>。创建 / 详情 / 更新均返回本对象，{@code config} 已由服务层解析为结构化模型。
 */
@Data
public class DashboardDetailVO {

    private Long id;

    private String name;

    /** 解析后的看板配置。 */
    private DashboardConfig config;

    /** 面板数量（冗余便于前端展示）。 */
    private Integer panelCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
