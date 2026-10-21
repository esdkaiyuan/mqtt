package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 看板分享（T-46）。
 * <p>
 * 一个分享可包含多个看板并按 {@code intervalSeconds} 轮播；令牌只存 SHA-256 哈希与短前缀，
 * 明文仅在创建 / 轮换时返回一次，避免数据库泄露直接获得访问权。
 * {@code dashboardIds} 为有序 JSON 数组文本，读取时由服务层解析。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("dashboard_share")
public class DashboardShare {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建者（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    /** 分享名称（大屏标题） */
    @TableField("name")
    private String name;

    /** 令牌 SHA-256 十六进制 */
    @TableField("token_hash")
    private String tokenHash;

    /** 令牌前缀，仅用于列表识别 */
    @TableField("token_preview")
    private String tokenPreview;

    /** 看板 ID JSON 数组（按轮播顺序） */
    @TableField("dashboard_ids")
    private String dashboardIds;

    /** 轮播间隔（秒） */
    @TableField("interval_seconds")
    private Integer intervalSeconds;

    /** 启用：1/0 */
    @TableField("enabled")
    private Integer enabled;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
