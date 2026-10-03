package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * OTA 逐台升级记录（T-22）。
 * <p>
 * 任务下一台设备一行，唯一键 {@code (task_id, device_id)}；状态流转
 * {@code PENDING → DISPATCHED → DOWNLOADING → FLASHING → SUCCESS}，旁支 {@code FAILED}/{@code TIMEOUT}。
 * 进度由设备上行主题 {@code device/{key}/ota} 回传，经 {@code OtaProgressService} 旁路更新，进度单调不减。
 * {@code command_id} 关联 {@code device_command_record.command_id}，便于跳转命令详情。
 * <p>
 * 表无逻辑删除列，仅随任务删除级联清理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ota_upgrade_record")
public class OtaUpgradeRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 升级任务（ota_upgrade_task.id） */
    @TableField("task_id")
    private Long taskId;

    /** 设备（device.id） */
    @TableField("device_id")
    private Long deviceId;

    /** 固件包（ota_firmware.id） */
    @TableField("firmware_id")
    private Long firmwareId;

    /** 目标版本号（冗余） */
    @TableField("version")
    private String version;

    /** 状态：PENDING/DISPATCHED/DOWNLOADING/FLASHING/SUCCESS/FAILED/TIMEOUT */
    @TableField("status")
    private String status;

    /** 进度百分比 0~100 */
    @TableField("progress")
    private Integer progress;

    /** 设备回传或系统填写的信息 */
    @TableField("message")
    private String message;

    /** 关联命令 ID（device_command_record.command_id） */
    @TableField("command_id")
    private String commandId;

    /** 最近一次成功下发时间 */
    @TableField("dispatched_at")
    private LocalDateTime dispatchedAt;

    /** 最近一次进度回传时间 */
    @TableField("last_report_at")
    private LocalDateTime lastReportAt;

    /** 创建时间，由表 DEFAULT 维护 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间，由表 ON UPDATE 维护 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
