package com.mqtt.cloud.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点位响应（T-31）
 * <p>
 * 出参刻意区分 {@code rawValue}（原始值）与 {@code value}（换算后工程值）并同时返回
 * {@code scale} / {@code offset}：现场排查「平台显示 3.6 但仪表盘是 0」这类问题时，
 * 需要同时看到原始寄存器值与换算参数才能判断是点表配错还是设备本身异常。
 */
@Data
@Builder
public class PointTableDTO {

    /** 点位ID */
    private Long id;

    /** 所属设备ID */
    private Long deviceId;

    /** 点位标识 */
    private String pointKey;

    /** 点位类型 */
    private String pointType;

    /** 地址 */
    private String address;

    /** 数据类型 */
    private String dataType;

    /** 换算系数 */
    private BigDecimal scale;

    /** 换算偏移 */
    private BigDecimal offset;

    /** 单位 */
    private String unit;

    /** 执行方 EDGE / PLATFORM */
    private String executor;

    /** 采集周期（毫秒） */
    private Integer readIntervalMs;

    /** 是否启用 */
    private Integer enabled;

    /** 最近一次采集时间；从未采集过为 {@code null}（区分「没配点」与「采不到值」） */
    private LocalDateTime lastCollectedAt;

    /** 最近一次采集状态 OK / FAILED / SKIPPED；从未采集过为 {@code null} */
    private String lastRunStatus;

    /** 最近一次失败原因；成功或从未采集为 {@code null} */
    private String lastErrorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
