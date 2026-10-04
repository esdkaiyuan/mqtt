package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 场景步骤执行明细实体（T-23 设计文档 §6.2）。
 * <p>
 * 对应数据库表：scene_step_run。一次执行下每个启用步骤一行，含延时快照、计划执行时刻、状态、
 * 尝试次数与重试锚点。**不设逻辑删除**，随所属执行记录由保留策略清理。
 * <p>
 * {@code next_attempt_at} 语义：{@code NULL} 表示**尚未排期**（前序步骤未完成）或已终态；
 * 巡检只拾取「非空且到期」的 {@code PENDING} 行，避免前序未完成时抢先执行。
 * {@code step_id} **无外键**（场景更新整体替换步骤），仅作快照引用。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("scene_step_run")
public class SceneStepRun {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 执行记录ID */
    @TableField("execution_id")
    private Long executionId;

    /** 场景ID（冗余，便于按场景排查） */
    @TableField("scene_id")
    private Long sceneId;

    /** 步骤ID（仅作快照引用，无外键） */
    @TableField("step_id")
    private Long stepId;

    /** 步骤序号快照 */
    @TableField("seq")
    private Integer seq;

    /** 动作类型快照 */
    @TableField("action_type")
    private String actionType;

    /** 延时快照（秒） */
    @TableField("delay_seconds")
    private Integer delaySeconds;

    /** 计划执行时刻（前一步完成 + delay）；NULL=尚未排期 */
    @TableField("scheduled_at")
    private LocalDateTime scheduledAt;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED */
    @TableField("status")
    private String status;

    /** 已尝试次数 */
    @TableField("attempt_count")
    private Integer attemptCount;

    /** 下次可执行时间；NULL=尚未排期（非终态且不参与巡检） */
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** 最近一次失败原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 转发载荷快照（HTTP/MQTT 步骤，便于排障） */
    @TableField("forward_payload")
    private String forwardPayload;

    /** 本步开始执行时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 本步终态时间 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}