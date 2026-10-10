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
 * 告警升级策略实体（T-27 设计文档 §6.4）。
 * <p>
 * 对应数据库表：alert_escalation_policy。按 {@code severity} 匹配未确认告警，超过
 * {@code after_seconds} 未确认即触发一次升级。{@code channel_ids} 以 JSON 文本映射
 * （与 {@code notification_channel.events} 同口径）。逻辑删除由全局配置
 * （{@code logic-delete-field: deleted}）处理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("alert_escalation_policy")
public class AlertEscalationPolicy {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 策略名称 */
    @TableField("name")
    private String name;

    /** 适用级别：ALL / INFO / WARNING / CRITICAL */
    @TableField("severity")
    private String severity;

    /** 未确认时长阈值（秒） */
    @TableField("after_seconds")
    private Integer afterSeconds;

    /** 升级时指派的处理人用户ID，可为空 */
    @TableField("assignee_id")
    private Long assigneeId;

    /** 升级时额外通知的渠道ID数组（JSON 文本），可为空 */
    @TableField("channel_ids")
    private String channelIds;

    /** 是否启用：1=启用，0=停用 */
    @TableField("enabled")
    private Integer enabled;

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