package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 点位采集执行日志实体（T-31）
 * <p>
 * 对应数据库表：{@code device_point_run}（由 V18 迁移创建）。
 * <p>
 * 平台侧主站采集与边缘驱动上报<b>统一留痕于此</b>，用于区分三类现场问题：
 * 点位没配（无记录）、配了但采不到（FAILED + errorMessage）、采到了但没变化（SKIPPED）。
 * 没有这张表时，这三类问题在监控上表现为同一个现象——「值没更新」，无法定位。
 * <p>
 * {@link #rawValue} / {@link #value} 刻意使用字符串承载：Modbus 原始值可能是 16 进制字、
 * 布尔量或多寄存器组合，统一字符串可避免数值精度与类型转换在存储层丢失信息。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_point_run")
public class DevicePointRun {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 点位ID */
    @TableField("point_id")
    private Long pointId;

    /** 所属设备ID（冗余字段，便于按设备维度直接检索，无需回表关联点表） */
    @TableField("device_id")
    private Long deviceId;

    /** 执行结果：OK / FAILED / SKIPPED */
    @TableField("status")
    private String status;

    /** 原始值（未经 scale/offset 换算） */
    @TableField("raw_value")
    private String rawValue;

    /** 换算后工程值 */
    @TableField("value")
    private String value;

    /** 失败原因（status = FAILED 时有值） */
    @TableField("error_message")
    private String errorMessage;

    /** 采集时间 */
    @TableField("collected_at")
    private LocalDateTime collectedAt;
}
