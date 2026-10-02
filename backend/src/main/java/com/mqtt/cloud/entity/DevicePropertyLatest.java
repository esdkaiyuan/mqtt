package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备属性最新值（T-14 解析产物）。
 * <p>
 * 只保留每个 {@code (device_id, identifier)} 的最新一条，时序历史归 T-21；
 * {@code value_text} 统一文本化，按 {@code data_type} 解释。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_property_latest")
public class DevicePropertyLatest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    @TableField("identifier")
    private String identifier;

    /** 落库时的物模型数据类型 */
    @TableField("data_type")
    private String dataType;

    /** 属性值（统一文本存储，按 data_type 解释） */
    @TableField("value_text")
    private String valueText;

    /** 设备上报时间（毫秒精度） */
    @TableField("reported_at")
    private LocalDateTime reportedAt;

    /** 云端更新时间，由表 ON UPDATE 维护 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}