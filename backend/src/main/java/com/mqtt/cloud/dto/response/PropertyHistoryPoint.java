package com.mqtt.cloud.dto.response;

import lombok.Data;

/**
 * 属性序列上的单个数据点（T-21 设计文档 §6.1）。
 * <p>
 * <b>只读投影，非表实体</b>。非数值型属性的 {@code min / max / avg} 恒为 {@code null}，前端只画 {@code count}。
 */
@Data
public class PropertyHistoryPoint {

    /** 桶起点，{@code yyyy-MM-dd HH:mm:ss}。 */
    private String time;

    /** 桶内样本数。 */
    private Integer count;

    /** 桶内最小值（非数值型为 null）。 */
    private Double min;

    /** 桶内最大值（非数值型为 null）。 */
    private Double max;

    /** 桶内均值（非数值型为 null）。 */
    private Double avg;
}
