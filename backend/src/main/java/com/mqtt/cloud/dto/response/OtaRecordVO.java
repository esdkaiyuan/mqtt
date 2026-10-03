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

    /** 记录状态：PENDING / DISPATCHED / DOWNLOADING / FLASHING / SUCCESS / FAILED / TIMEOUT。 */
    private String status;

    /** 进度百分比 0~100。 */
    private Integer progress;

    /** 设备回传或系统填写的信息。 */
    private String message;

    /** 关联命令 ID。 */
    private String commandId;

    /** 最近一次成功下发时间。 */
    private LocalDateTime dispatchedAt;

    /** 最近一次进度回传时间。 */
    private LocalDateTime lastReportAt;
}
