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
 * 站内信实体（T-27 设计文档 §6.1）。
 * <p>
 * 对应数据库表：notification。内置必达通道的落库载体，一次事件写入一行；
 * {@code read_at} 为 NULL 表示未读，列表与未读数均按 {@code user_id} 隔离。
 * 无审计更新列：站内信只新增与置已读（{@code read_at}），不改正文。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("notification")
public class Notification {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 来源事件名，如 {@code alert.triggered} */
    @TableField("event_type")
    private String eventType;

    /** 展示级别：INFO / WARNING / CRITICAL */
    @TableField("level")
    private String level;

    /** 标题 */
    @TableField("title")
    private String title;

    /** 正文 */
    @TableField("content")
    private String content;

    /** 关联对象类型，本期为 ALERT */
    @TableField("ref_type")
    private String refType;

    /** 关联对象主键（告警记录 ID） */
    @TableField("ref_id")
    private Long refId;

    /** 已读时间，NULL 表示未读 */
    @TableField("read_at")
    private LocalDateTime readAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}