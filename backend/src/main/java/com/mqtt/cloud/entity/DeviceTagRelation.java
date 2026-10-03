package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备-标签关联实体（T-18 设计文档 §6）。
 * <p>
 * 对应数据库表：device_tag_relation，**物理行**（无逻辑删除）：解除关联即物理删除，
 * 由唯一键 {@code uk_device_tag(device_id, tag_id)} 保证重复关联幂等。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_tag_relation")
public class DeviceTagRelation {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private Long deviceId;

    /** 标签ID */
    @TableField("tag_id")
    private Long tagId;

    /** 关联时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}