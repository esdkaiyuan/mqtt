package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * OTA 升级批次（T-32，灰度分批 G-11-1）。
 * <p>
 * 一个任务按 {@code batch_size} 切成若干批次，唯一键 {@code (task_id, batch_no)}。批次是灰度的**调度单元**：
 * {@code PENDING} 批次由 {@code OtaBatchSweeper} 按 {@code app.ota.batch-interval-ms} 自动推进，
 * 或由控制台手动触发；任务 {@code PAUSED} 时批次停在 {@code PENDING}，任务 {@code ABORTED} 时剩余批次置 {@code SKIPPED}。
 * <p>
 * 计数与 {@code status} 由 {@code refreshBatchCounters} 聚合 {@code ota_upgrade_record} 中同
 * {@code (task_id, batch_no)} 的记录重算，与任务级 {@code refreshCounters} 同款口径，不在读路径上推算。
 * <p>
 * 自动推进的判定**不依赖本表的 {@code status}**（它可能因刷新时机滞后而过时），而是直接查
 * {@code ota_upgrade_record} 是否还有非终态行 —— 数据源自洽，见 {@code selectDueBatches}。
 * {@code scheduled_at} 仅为排期参考与前端展示，不参与判定。
 * <p>
 * 表无逻辑删除列，仅随任务删除级联清理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ota_upgrade_batch")
public class OtaUpgradeBatch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 升级任务（ota_upgrade_task.id） */
    @TableField("task_id")
    private Long taskId;

    /** 批号，从 1 起 */
    @TableField("batch_no")
    private Integer batchNo;

    /** 本批设备数 */
    @TableField("device_count")
    private Integer deviceCount;

    /** 本批已成功下发数（不含 SKIPPED） */
    @TableField("dispatched_count")
    private Integer dispatchedCount;

    /** 本批升级成功数 */
    @TableField("success_count")
    private Integer successCount;

    /** 本批升级失败/超时数 */
    @TableField("failed_count")
    private Integer failedCount;

    /** 本批被终止跳过数 */
    @TableField("skipped_count")
    private Integer skippedCount;

    /** 批次状态：PENDING/RUNNING/SUCCESS/PARTIAL/FAILED/SKIPPED */
    @TableField("status")
    private String status;

    /** 排期参考时间（按批次序号推算），不参与自动推进判定 */
    @TableField("scheduled_at")
    private LocalDateTime scheduledAt;

    /** 实际下发时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 进入终态时间，自动推进间隔的起算点 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}