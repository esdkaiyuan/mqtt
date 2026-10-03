package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备-分组关联实体（T-18 设计文档 §6）。
 * <p>
 * 对应数据库表：device_group_relation，**物理行**（无逻辑删除）：解除关联即物理删除，
 * 由唯一键 {@code uk_device_group(device_id, group_id)} 保证重复关联幂等。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_group_relation")
public class DeviceGroupRelation {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private Long deviceId;

    /** 分组ID */
    @TableField("group_id")
    private Long groupId;

    /** 关联时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}