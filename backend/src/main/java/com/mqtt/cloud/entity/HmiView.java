package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 快速上位机（T-38）。
 * <p>
 * 一个 HMI 固定绑定一台设备，{@code config} 以 JSON 原文承载控件数组，
 * 由服务层解析为 {@code HmiConfig} 后校验。与看板一样仅归属创建者本人，删除为硬删除。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("hmi_view")
public class HmiView {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建者（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    /** 绑定设备（device.id） */
    @TableField("device_id")
    private Long deviceId;

    @TableField("name")
    private String name;

    /** 控件配置（JSON 文本） */
    @TableField("config")
    private String config;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
