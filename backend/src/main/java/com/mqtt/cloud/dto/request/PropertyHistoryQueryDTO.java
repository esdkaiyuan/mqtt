package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 属性时序聚合查询 DTO（T-21 设计文档 §6.1）。
 * <p>
 * 时间字段用 {@code String}（与 {@code DeviceLogQueryDTO} 一致），由服务层解析为 {@code LocalDateTime}
 * 并做格式 / 跨度校验；{@code deviceIds} 与 {@code identifiers} 支持查询串逗号分隔（Spring 自动切分）。
 */
@Data
public class PropertyHistoryQueryDTO {

    /** 设备 ID 列表，必填；逐一做归属校验（不存在 → 2002，非本人 → 2003）。 */
    private List<Long> deviceIds;

    /** 属性标识符列表，必填；逐一做物模型校验（未建模 → 6226）。 */
    private List<String> identifiers;

    /** 起始时间（含），{@code yyyy-MM-dd HH:mm:ss} 或 ISO。 */
    private String startTime;

    /** 结束时间（不含，半开区间）。 */
    private String endTime;

    /** 时间桶，白名单：{@code 1m / 5m / 15m / 30m / 1h / 6h / 1d}（非法 → 6227）。 */
    private String bucket;
}
