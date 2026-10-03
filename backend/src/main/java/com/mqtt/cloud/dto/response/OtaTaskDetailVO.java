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

    /** 任务状态：RUNNING / SUCCESS / PARTIAL / FAILED。 */
    private String status;

    /** 目标快照（deviceIds / productIds / groupIds / tagIds）。 */
    private BatchTargetRequest target;

    private LocalDateTime createdAt;
}
