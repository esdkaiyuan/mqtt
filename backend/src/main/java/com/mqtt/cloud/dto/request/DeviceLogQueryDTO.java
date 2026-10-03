package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 设备日志查询 DTO（T-20 设计文档 §6.1）。
 * <p>
 * 设备 ID 由 path 传入，不在此 DTO 中。时间字段用 {@code String}（与 {@code MessageQueryDTO} 一致），
 * 由服务层解析为 {@code LocalDateTime} 并做格式 / 跨度校验。
 */
@Data
public class DeviceLogQueryDTO {

    /** 日志类型过滤，可重复；为空 = 全部。取值 MESSAGE / COMMAND / EVENT / STATUS。 */
    private List<String> types;

    /** 起始时间（含），{@code yyyy-MM-dd HH:mm:ss} 或 ISO。 */
    private String startTime;

    /** 结束时间（不含）。 */
    private String endTime;

    /** 关键字，匹配 topic / identifier / error_message / payload（LIKE，选填）。 */
    private String keyword;

    /** 页码，默认 1。 */
    private Integer pageNum = 1;

    /** 每页数量，默认 20，上限取配置。 */
    private Integer pageSize = 20;
}
