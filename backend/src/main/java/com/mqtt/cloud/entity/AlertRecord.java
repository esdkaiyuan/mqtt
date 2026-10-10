package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 告警记录实体（T-17 设计文档 §6）。
 * <p>
 * 对应数据库表：alert_record。一次「活动告警」的持久化实体，跨多次重复触发复用一行，
 * 直到 {@code RECOVERED}（终态）。**不设逻辑删除**：记录保留完整生命周期，仅通过状态收敛。
 * 冗余快照 {@code device_key} / {@code rule_name} / {@code severity} 保证设备或规则改名后历史仍可读。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("alert_record")
public class AlertRecord {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID（冗余，便于列表隔离） */
    @TableField("user_id")
    private Long userId;

    /** 设备ID */
    @TableField("device_id")
    private Long deviceId;

    /** 设备标识快照 */
    @TableField("device_key")
    private String deviceKey;

    /** 规则ID */
    @TableField("rule_id")
    private Long ruleId;

    /** 规则名快照 */
    @TableField("rule_name")
    private String ruleName;

    /** 来源快照：THRESHOLD/OFFLINE/EVENT */
    @TableField("source_type")
    private String sourceType;

    /** 级别快照：INFO/WARNING/CRITICAL */
    @TableField("severity")
    private String severity;

    /** 属性/事件标识符 */
    @TableField("identifier")
    private String identifier;

    /** 告警标题 */
    @TableField("title")
    private String title;

    /** 触发值 / 事件输出（归一化文本或 JSON） */
    @TableField("trigger_value")
    private String triggerValue;

    /** 状态：TRIGGERED/ACKNOWLEDGED/RECOVERED */
    @TableField("status")
    private String status;

    /** 累计触发次数（抑制窗口内累加） */
    @TableField("trigger_count")
    private Integer triggerCount;

    /** 首次触发时间 */
    @TableField("first_triggered_at")
    private LocalDateTime firstTriggeredAt;

    /** 最近触发时间（抑制窗口基准） */
    @TableField("last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 最近一次回调通知时间 */
    @TableField("notified_at")
    private LocalDateTime notifiedAt;

    /** 确认时间 */
    @TableField("acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** 确认人用户ID */
    @TableField("acknowledged_by")
    private Long acknowledgedBy;

    /** 恢复时间 */
    @TableField("recovered_at")
    private LocalDateTime recoveredAt;

    /**
     * 处理人用户ID，NULL 表示未指派（T-27）。
     * <p>
     * 更新策略固定 {@link FieldStrategy#ALWAYS}：解除指派需把该列显式置空，
     * 若沿用默认 {@code NOT_NULL} 策略，null 值会被 MP 跳过而无法解除。
     */
    @TableField(value = "assignee_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long assigneeId;

    /** 已升级次数，0 表示未升级（T-27） */
    @TableField("escalation_level")
    private Integer escalationLevel;

    /** 最近升级时间，作为下一级升级的判定基准（T-27） */
    @TableField("last_escalated_at")
    private LocalDateTime lastEscalatedAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
