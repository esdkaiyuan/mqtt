package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mqtt.cloud.dto.response.SceneConditionView;
import com.mqtt.cloud.dto.response.SceneStepView;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景定义实体（T-23 设计文档 §6.2）。
 * <p>
 * 对应数据库表：scene_definition。一个场景 = 触发源（{@code triggerType}，含可选触发比较条件或 cron）
 * + 条件组（{@code conditionLogic} + {@code conditionConfig}）+ 冷却窗口；动作流步骤在
 * {@link SceneStep}（一对多，按 {@code seq} 有序）。
 * <p>
 * {@code conditionConfig} 在库中为 JSON 文本（{@code TEXT}），返回前端时由服务层反序列化到瞬态字段
 * {@link #conditionsView}；步骤流同理反序列化到 {@link #stepsView}。持久化字段本身不暴露给前端，
 * 避免前端拿到双重形态。逻辑删除由全局配置（{@code logic-delete-field: deleted}）处理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("scene_definition")
public class SceneDefinition {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 场景名称 */
    @TableField("name")
    private String name;

    /** 描述 */
    @TableField("description")
    private String description;

    /** 触发源：PROPERTY/EVENT/TIMER */
    @TableField("trigger_type")
    private String triggerType;

    /** 触发限定设备ID，NULL=该用户全部设备（TIMER 源为空） */
    @TableField("trigger_device_id")
    private Long triggerDeviceId;

    /** 触发属性/事件标识符（PROPERTY/EVENT 必填） */
    @TableField("trigger_identifier")
    private String triggerIdentifier;

    /** 触发比较符：GT/GTE/LT/LTE/EQ/NE（空=任意上报即触发） */
    @TableField("trigger_operator")
    private String triggerOperator;

    /** 触发比较阈值（文本，按物模型类型解释） */
    @TableField("trigger_threshold")
    private String triggerThreshold;

    /** 事件类型过滤（EVENT 源可空） */
    @TableField("trigger_event_type")
    private String triggerEventType;

    /** 定时触发 cron（5 字段，分钟级；TIMER 源必填） */
    @TableField("timer_cron")
    private String timerCron;

    /** 条件组合：AND/OR（缺省 AND；条件组为空表示无附加条件） */
    @TableField("condition_logic")
    private String conditionLogic;

    /** 条件组 JSON 数组，库中为文本；响应中由 {@link #conditionsView} 以对象形态暴露 */
    @TableField("condition_config")
    @JsonIgnore
    private String conditionConfig;

    /** 触发冷却窗口（秒），0=不限制 */
    @TableField("cooldown_seconds")
    private Integer cooldownSeconds;

    /** 最近一次触发时间（展示 + 定时触发分钟去重锚点） */
    @TableField("last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 是否启用：1/0 */
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

    /** 返回给前端的已解析条件组（设计文档 §10.1）；非持久化字段，JSON 键名与请求体一致为 {@code conditions}。 */
    @TableField(exist = false)
    @JsonProperty("conditions")
    private List<SceneConditionView> conditionsView;

    /** 返回给前端的已解析步骤流（设计文档 §10.1）；非持久化字段，JSON 键名与请求体一致为 {@code steps}。 */
    @TableField(exist = false)
    @JsonProperty("steps")
    private List<SceneStepView> stepsView;
}