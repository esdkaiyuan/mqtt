package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备分组实体（T-18 设计文档 §6）。
 * <p>
 * 对应数据库表：device_group，自引用树（{@code parent_id=NULL} 为根）。
 * 逻辑删除由全局配置（{@code logic-delete-field: deleted}）处理。
 * <p>
 * {@code children} / {@code deviceCount} 为树投影瞬态字段，不映射数据库。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_group")
public class DeviceGroup {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 父分组ID，NULL=根分组。updateStrategy=ALWAYS 保证「移动到根」时能显式写入 NULL。 */
    @TableField(value = "parent_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    /** 分组名称 */
    @TableField("name")
    private String name;

    /** 同级排序（升序） */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 描述。updateStrategy=ALWAYS 保证可清空。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记（0=未删除，1=已删除） */
    @TableField("deleted")
    private Integer deleted;

    /** 子分组（树投影用，不映射数据库） */
    @TableField(exist = false)
    private List<DeviceGroup> children;

    /** 直接关联设备数（树投影用，不映射数据库） */
    @TableField(exist = false)
    private Integer deviceCount;
}