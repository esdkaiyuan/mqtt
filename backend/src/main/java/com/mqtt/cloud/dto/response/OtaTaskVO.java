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

    /** 任务状态：RUNNING / SUCCESS / PARTIAL / FAILED。 */
    private String status;

    private LocalDateTime createdAt;
}
