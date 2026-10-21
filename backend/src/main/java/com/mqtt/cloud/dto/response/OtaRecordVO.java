package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OTA 逐台升级记录（T-22 设计文档 §6.2）。
 * <p>
 * <b>只读投影，非表实体</b>。设备名称 / {@code deviceKey} 由服务层回填（表内只存 {@code device_id}）。
 */
@Data
public class OtaRecordVO {

    private Long deviceId;

    /** 设备名称（服务层回填）。 */
    private String deviceName;

    /** 设备标识（服务层回填）。 */
    private String deviceKey;

    /** 目标固件版本。 */
    private String version;

    /** 所属批号（T-32）。 */
    private Integer batchNo;

    /** 记录状态：PENDING / DISPATCHED / DOWNLOADING / FLASHING / VERIFYING / SUCCESS / FAILED / TIMEOUT / SKIPPED。 */
    private String status;

    /** 进度百分比 0~100。 */
    private Integer progress;

    /** 固件校验结果（T-32）：NOT_REPORTED / VERIFYING / PASSED / MISMATCHED。 */
    private String verifyStatus;

    /** 设备回传的固件 md5（T-32），与平台固件 md5 不一致时状态为 FAILED。 */
    private String reportedMd5;

    /** 设备回传或系统填写的信息。 */
    private String message;

    /** 关联命令 ID。 */
    private String commandId;

    /** 最近一次成功下发时间。 */
    private LocalDateTime dispatchedAt;

    /** 最近一次进度回传时间。 */
    private LocalDateTime lastReportAt;
}
