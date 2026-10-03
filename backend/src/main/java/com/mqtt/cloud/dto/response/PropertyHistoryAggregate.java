package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 属性分桶聚合的行投影（T-21 设计文档 §7.2）。
 * <p>
 * <b>只读查询投影，非表实体</b>：由 {@code DevicePropertyHistoryMapper.aggregateByBucket} 直接映射，
 * 列别名与字段名一一对应（{@code sampleCount} / {@code minValue} / {@code maxValue} / {@code avgValue}，
 * 刻意避开 {@code count / min / max / avg} 这些 SQL 保留函数名）。服务层再映射为对外
 * {@link PropertyHistoryPoint}，把投影命名与接口契约分离。
 */
@Data
public class PropertyHistoryAggregate {

    private Long deviceId;

    private String identifier;

    /** 分桶起点（桶内所有样本的所属桶）。 */
    private LocalDateTime time;

    /** 桶内样本数。 */
    private Integer sampleCount;

    /** 数值型桶内最小值；非数值型为 {@code null}。 */
    private BigDecimal minValue;

    /** 数值型桶内最大值；非数值型为 {@code null}。 */
    private BigDecimal maxValue;

    /** 数值型桶内均值；非数值型为 {@code null}。 */
    private BigDecimal avgValue;
}
