package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板列表项（T-21 设计文档 §6.2）。
 * <p>
 * <b>只读投影，非表实体</b>。列表**不含** {@code config} 明细，仅给出面板数，避免列表接口搬运大字段。
 */
@Data
public class DashboardSummaryVO {

    private Long id;

    private String name;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 面板数量。 */
    private Integer panelCount;
}
