package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备影子（T-16）。
 * <p>
 * 一设备一行（{@code uk_shadow_device}），懒创建。{@code desired} / {@code reported} / {@code delta}
 * 均为「identifier -> 归一化文本」的 JSON 映射；{@code version} 由 SQL {@code version = version + 1}
 * 单调递增，应用侧以 CAS（{@code WHERE device_id = ? AND version = ?}）避免并发合并丢失更新。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_shadow")
public class DeviceShadow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    /** 期望状态：identifier -> 归一化文本（JSON 文本） */
    @TableField("desired")
    private String desired;

    /** 上报状态：identifier -> 归一化文本（JSON 文本） */
    @TableField("reported")
    private String reported;

    /** 差异：desired 中与 reported 不一致（含缺失）的键（JSON 文本） */
    @TableField("delta")
    private String delta;

    /** 影子版本号，单调递增 */
    @TableField("version")
    private Long version;

    /** 创建时间（毫秒精度） */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 最近变更时间（毫秒精度） */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
