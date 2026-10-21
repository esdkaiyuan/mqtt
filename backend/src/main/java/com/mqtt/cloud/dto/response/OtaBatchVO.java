package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OTA 升级批次只读投影（T-32）。
 * <p>
 * <b>非表实体</b>：字段与 {@code ota_upgrade_batch} 一一对应，前端灰度面板按 {@code batchNo} 升序渲染时间线。
 */
@Data
public class OtaBatchVO {

    private Long id;

    /** 批号，从 1 起。 */
    private Integer batchNo;

    /** 本批设备数。 */
    private Integer deviceCount;

    /** 本批已成功下发数（不含被跳过的）。 */
    private Integer dispatchedCount;

    /** 本批升级成功数。 */
    private Integer successCount;

    /** 本批升级失败/超时数。 */
    private Integer failedCount;

    /** 本批被终止跳过数（不计入失败）。 */
    private Integer skippedCount;

    /** 批次状态：PENDING / RUNNING / SUCCESS / PARTIAL / FAILED / SKIPPED。 */
    private String status;

    /** 排期参考时间，不参与自动推进判定。 */
    private LocalDateTime scheduledAt;

    /** 实际下发时间。 */
    private LocalDateTime startedAt;

    /** 进入终态时间，自动推进间隔的起算点。 */
    private LocalDateTime finishedAt;
}