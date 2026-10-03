package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备属性历史（T-21 解析产物，追加写时序数据）。
 * <p>
 * 与 {@link DevicePropertyLatest} 同族：{@code value_text} 统一文本化，按 {@code data_type} 解释；
 * 区别在于本表不做去重、不取最新，只按 {@code (device_id, identifier, reported_at)} 追加。
 * 历史由保留巡检按 {@code reported_at} 分批硬删除，故无逻辑删除字段。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_property_history")
public class DevicePropertyHistory {

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

    /** 入库时间，由表 DEFAULT CURRENT_TIMESTAMP(3) 维护 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
