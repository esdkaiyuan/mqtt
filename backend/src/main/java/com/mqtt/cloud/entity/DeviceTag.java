package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备标签实体（T-18 设计文档 §6）。
 * <p>
 * 对应数据库表：device_tag，扁平标签（无层级），含展示色。
 * 逻辑删除由全局配置（{@code logic-delete-field: deleted}）处理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_tag")
public class DeviceTag {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 标签名称 */
    @TableField("name")
    private String name;

    /** 展示色（如 #409EFF）。updateStrategy=ALWAYS 保证可清空。 */
    @TableField(value = "color", updateStrategy = FieldStrategy.ALWAYS)
    private String color;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记（0=未删除，1=已删除） */
    @TableField("deleted")
    private Integer deleted;
}