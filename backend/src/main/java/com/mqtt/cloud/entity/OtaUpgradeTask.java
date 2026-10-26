package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * OTA 升级任务（T-22）。
 * <p>
 * {@code target_json} 记录创建时的目标快照（{@code deviceIds/productIds/groupIds/tagIds}），
 * 以 {@code String} 承载原始 JSON，读写时由服务层用 {@code ObjectMapper} 解析，避免实体耦合 Jackson 注解
 * （与 {@link Dashboard} 同款做法）。
 * <p>
 * 计数（{@code total/dispatched/success/failed/skipped}）与 {@code status} 由 {@code refreshCounters}
 * 聚合 {@code ota_upgrade_record} 重算，不在读路径上推算；唯 {@code PAUSED} / {@code ABORTED}
 * 两个由运维操作产生的状态不被聚合覆盖（T-32）。
 * 灰度字段（{@code batchMode/batchSize/batchIntervalMs/batchTotal/currentBatchNo}）为 T-32 新增，
 * 存量行由 V19 回填为 {@code ALL} 策略，与改造前的一次性全量下发等价。
 * {@code firmware_id} 外键不级联：固件被任务引用时禁止删除，由服务层前置拦截。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ota_upgrade_task")
public class OtaUpgradeTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建者（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    /** 固件包（ota_firmware.id），不级联删除 */
    @TableField("firmware_id")
    private Long firmwareId;

    /** 固件所属产品（冗余，便于筛选） */
    @TableField("product_id")
    private Long productId;

    /** 任务名称 */
    @TableField("name")
    private String name;

    /** 目标快照（deviceIds/productIds/groupIds/tagIds，JSON 文本） */
    @TableField("target_json")
    private String targetJson;

    /** 目标设备总数 */
    @TableField("total_count")
    private Integer totalCount;

    /** 已成功下发数 */
    @TableField("dispatched_count")
    private Integer dispatchedCount;

    /** 升级成功数 */
    @TableField("success_count")
    private Integer successCount;

    /** 升级失败/超时数 */
    @TableField("failed_count")
    private Integer failedCount;

    /**
     * 被终止跳过的设备数（T-32）。
     * <p>
     * 单独计数而非并入 {@code failedCount}：终止是运维主动终止，不应被读成「这批设备升级失败」。
     */
    @TableField("skipped_count")
    private Integer skippedCount;

    /** 建任务时因设备当前固件版本高于目标版本而被跳过的台数（T-54）。 */
    private Integer downgradeSkippedCount;

    /** 任务状态：RUNNING/SUCCESS/PARTIAL/FAILED（T-32 增 PAUSED/ABORTED） */
    @TableField("status")
    private String status;

    /** 下发策略：ALL=一次性全量 / BATCHED=灰度分批（T-32） */
    @TableField("batch_mode")
    private String batchMode;

    /** 每批台数，BATCHED 时生效；0 表示不分批（T-32） */
    @TableField("batch_size")
    private Integer batchSize;

    /** 批次自动推进间隔（毫秒），0=仅手动推进（T-32） */
    @TableField("batch_interval_ms")
    private Long batchIntervalMs;

    /** 总批次数；ALL 恒为 1（T-32） */
    @TableField("batch_total")
    private Integer batchTotal;

    /** 已下发的最大批号，0=尚未下发任何批次（T-32） */
    @TableField("current_batch_no")
    private Integer currentBatchNo;

    /** 创建时间，由表 DEFAULT 维护 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间，由表 ON UPDATE 维护 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
