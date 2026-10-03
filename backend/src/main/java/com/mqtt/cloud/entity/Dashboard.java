package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 可保存看板（T-21）。
 * <p>
 * {@code config} 以 {@code String} 承载原始 JSON（面板数组），读写时由服务层用 {@code ObjectMapper}
 * 解析为 {@code DashboardConfig}，避免实体耦合 Jackson 注解（与 {@link DeviceShadow} 同款做法）。
 * 看板仅归属创建者本人，删除为硬删除。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("dashboard")
public class Dashboard {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建者（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    @TableField("name")
    private String name;

    /** 看板配置（面板数组，JSON 文本） */
    @TableField("config")
    private String config;

    /** 创建时间，由表 DEFAULT 维护 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间，由表 ON UPDATE 维护 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
