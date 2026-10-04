package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 场景执行记录实体（T-23 设计文档 §6.2）。
 * <p>
 * 对应数据库表：scene_execution。一次场景触发（自动 / 手动）的落库记录，含触发快照、进度
 * （{@code totalSteps} / {@code finishedSteps}）与终态。步骤明细在 {@link SceneStepRun}。
 * **不设逻辑删除**：记录只增不删，由保留策略按批物理清理终态记录（{@code PENDING} / {@code RUNNING} 永不清理）。
 * 冗余快照 {@code scene_name} / {@code trigger_device_key} / {@code trigger_device_name}
 * 保证场景或设备改名后历史仍可读。
 * <p>
 * 状态流转：{@code PENDING}（已触发待首步）→ {@code RUNNING}（有步骤执行中）→ {@code SUCCESS} / {@code FAILED}。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("scene_execution")
public class SceneExecution {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID（冗余，便于按用户查询） */
    @TableField("user_id")
    private Long userId;

    /** 场景ID */
    @TableField("scene_id")
    private Long sceneId;

    /** 场景名称快照 */
    @TableField("scene_name")
    private String sceneName;

    /** 触发源快照 */
    @TableField("trigger_type")
    private String triggerType;

    /** 触发设备ID（TIMER 源为空） */
    @TableField("trigger_device_id")
    private Long triggerDeviceId;

    /** 触发设备标识快照 */
    @TableField("trigger_device_key")
    private String triggerDeviceKey;

    /** 触发设备名称快照 */
    @TableField("trigger_device_name")
    private String triggerDeviceName;

    /** 触发标识符快照 */
    @TableField("trigger_identifier")
    private String triggerIdentifier;

    /** 触发值文本快照 */
    @TableField("trigger_value")
    private String triggerValue;

    /** 触发事件类型快照 */
    @TableField("trigger_event_type")
    private String triggerEventType;

    /** 触发方式：AUTO=自动 / MANUAL=手动执行 */
    @TableField("trigger_source")
    private String triggerSource;

    /** 启用步骤总数快照 */
    @TableField("total_steps")
    private Integer totalSteps;

    /** 已完成（SUCCESS/SKIPPED）步骤数 */
    @TableField("finished_steps")
    private Integer finishedSteps;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED */
    @TableField("status")
    private String status;

    /** 最近一次失败原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 首个步骤开始执行时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

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