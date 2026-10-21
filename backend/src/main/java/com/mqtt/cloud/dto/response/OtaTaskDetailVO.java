package com.mqtt.cloud.dto.response;

import com.mqtt.cloud.dto.request.BatchTargetRequest;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OTA 升级任务详情（T-22 设计文档 §6.2）。
 * <p>
 * <b>只读投影，非表实体</b>。在 {@link OtaTaskVO} 全字段基础上，额外给出创建时的
 * {@code target} 目标快照（由服务层解析 {@code target_json} 得到）。
 */
@Data
public class OtaTaskDetailVO {

    private Long id;

    private String name;

    private Long firmwareId;

    /** 固件版本号（服务层回填）。 */
    private String version;

    private Long productId;

    /** 产品名称（服务层回填）。 */
    private String productName;

    /** 目标设备总数。 */
    private Integer totalCount;

    /** 已成功下发数。 */
    private Integer dispatchedCount;

    /** 升级成功数。 */
    private Integer successCount;

    /** 升级失败/超时数。 */
    private Integer failedCount;

    /** 被终止跳过的设备数，不计入失败（T-32）。 */
    private Integer skippedCount;

    /** 任务状态：RUNNING / PAUSED / ABORTED / SUCCESS / PARTIAL / FAILED。 */
    private String status;

    /** 下发策略：ALL 一次性全量 / BATCHED 灰度分批（T-32）。 */
    private String batchMode;

    /** 每批台数；{@code ALL} 策略为 0（T-32）。 */
    private Integer batchSize;

    /** 批次自动推进间隔（毫秒），0 = 仅手动推进（T-32）。 */
    private Long batchIntervalMs;

    /** 总批次数（T-32）。 */
    private Integer batchTotal;

    /** 已下发的最大批号，0 = 尚未下发任何批次（T-32）。 */
    private Integer currentBatchNo;

    /** 因设备已在其他升级任务中而被互斥剔除的台数（T-32 / G-11-4）。 */
    private Integer conflictSkippedCount;

    /** 批次列表（T-32 灰度时间线）；存量任务可能为空列表，前端据此隐藏灰度面板。 */
    private java.util.List<OtaBatchVO> batches;

    /** 目标快照（deviceIds / productIds / groupIds / tagIds）。 */
    private BatchTargetRequest target;

    private LocalDateTime createdAt;
}
