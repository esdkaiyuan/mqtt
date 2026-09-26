package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 历史记录实体
 * 对应数据库表：history_record
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("history_record")
public class HistoryRecord {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private Long deviceId;

    /** Topic路径 */
    @TableField("topic")
    private String topic;

    /** 数据载荷（JSON格式） */
    @TableField("payload")
    private String payload;

    /** 数据记录时间 */
    @TableField("timestamp")
    private LocalDateTime timestamp;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
