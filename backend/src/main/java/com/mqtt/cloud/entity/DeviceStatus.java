package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备状态历史实体
 * 对应数据库表：device_status_history
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_status_history")
public class DeviceStatus {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private Long deviceId;

    /** 设备状态：ONLINE/OFFLINE */
    @TableField("status")
    private String status;

    /** 状态变更时间 */
    @TableField("timestamp")
    private LocalDateTime timestamp;
}
