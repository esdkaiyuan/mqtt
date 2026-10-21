package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 规则执行记录实体（T-19 设计文档 §6.2）。
 * <p>
 * 对应数据库表：rule_execution。一次规则触发的落库记录，含状态流转、尝试次数、重试锚点与载荷快照。
 * **不设逻辑删除**：记录只增不删，由保留策略按批物理清理终态记录（{@code PENDING} 永不清理）。
 * 冗余快照 {@code rule_name} / {@code device_key} / {@code device_name} 保证规则或设备改名后历史仍可读。
 * <p>
 * {@code next_attempt_at} 语义：{@code NULL} 表示无待重试（已投递待首次执行 / 已终态）；
 * 巡检只拾取「非空且到期」的 {@code PENDING} 记录，避免与线程池内的首次执行并发重复执行。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("rule_execution")
public class RuleExecution {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID（冗余，便于按用户查询） */
    @TableField("user_id")
    private Long userId;

    /** 规则ID */
    @TableField("rule_id")
    private Long ruleId;

    /** 规则名称快照 */
    @TableField("rule_name")
    private String ruleName;

    /** 设备ID */
    @TableField("device_id")
    private Long deviceId;

    /** 设备标识快照 */
    @TableField("device_key")
    private String deviceKey;

    /** 设备名称快照 */
    @TableField("device_name")
    private String deviceName;

    /** 触发源快照：PROPERTY/EVENT */
    @TableField("source_type")
    private String sourceType;

    /** 触发标识符 */
    @TableField("identifier")
    private String identifier;

    /** 触发值文本 */
    @TableField("trigger_value")
    private String triggerValue;

    /** 动作类型快照 */
    @TableField("action_type")
    private String actionType;

    /**
     * 该次执行对应的动作配置快照（T-33 / G-6-7，多动作为 NULL 时回查规则当前 {@code action_config}）。
     * <p>
     * 多动作时一次触发落 N 条执行记录，各自携带 {@code actions_json} 中对应项的配置 ——
     * 执行器按记录 ID 回查规则拿不到「第几个动作」，快照列是让重试与巡检重放各自拿到正确配置的依据。
     */
    @TableField("action_config_snapshot")
    @JsonIgnore
    private String actionConfigSnapshot;

    /** 状态：PENDING/SUCCESS/FAILED */
    @TableField("status")
    private String status;

    /** 已尝试次数 */
    @TableField("attempt_count")
    private Integer attemptCount;

    /** 下次重试时间；NULL 表示无待重试（已投递待首次执行 / 已终态） */
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** 最近一次失败原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 转发载荷快照（HTTP/MQTT 动作，便于排障） */
    @TableField("forward_payload")
    private String forwardPayload;

    /** 终态时间（SUCCESS/FAILED） */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}