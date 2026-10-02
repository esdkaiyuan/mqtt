package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备事件记录（T-14 解析产物）。
 * <p>
 * 追加型，不去重（去重属告警中心 L4 的抑制窗口职责）；
 * {@code event_type} 取自落库时的物模型定义，而非上报载荷。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_event_record")
public class DeviceEventRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    @TableField("identifier")
    private String identifier;

    /** info / alert / fault（落库时的物模型定义） */
    @TableField("event_type")
    private String eventType;

    /** 事件输出参数（JSON 文本） */
    @TableField("output_data")
    private String outputData;

    /** 设备上报时间（毫秒精度） */
    @TableField("reported_at")
    private LocalDateTime reportedAt;

    /** 入库时间，由表默认值维护 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}