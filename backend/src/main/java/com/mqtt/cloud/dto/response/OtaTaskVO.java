package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OTA 升级任务列表项（T-22 设计文档 §6.2）。
 * <p>
 * <b>只读投影，非表实体</b>。计数与状态取自 {@code ota_upgrade_task} 的聚合结果，
 * 列表不搬运目标快照（{@code target_json}）与逐台记录。
 */
@Data
public class OtaTaskVO {

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

    /** 建任务时因当前固件版本高于目标版本而被跳过的台数（T-54）。 */
    private Integer downgradeSkippedCount;

    /** 任务状态：RUNNING / PAUSED / ABORTED / SUCCESS / PARTIAL / FAILED。 */
    private String status;

    /** 下发策略：ALL 一次性全量 / BATCHED 灰度分批（T-32）。 */
    private String batchMode;

    /** 总批次数（T-32）；{@code ALL} 恒为 1。 */
    private Integer batchTotal;

    /** 已下发的最大批号（T-32）。 */
    private Integer currentBatchNo;

    private LocalDateTime createdAt;
}
