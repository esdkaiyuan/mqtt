package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息规则定义实体（T-19 设计文档 §6.2）。
 * <p>
 * 对应数据库表：rule_definition。一条规则 = 触发源（{@code sourceType}）+ 条件
 * （属性源为「标识符 + 比较符 + 阈值」，事件源为「标识符 + 可选事件类型」）+ 动作
 * （{@code actionType} + {@code actionConfig}）+ 冷却窗口。
 * <p>
 * {@code actionConfig} 在库中为 JSON 文本（{@code TEXT}），返回前端时由服务层反序列化到瞬态字段
 * {@link #actionConfigView}；持久化字段本身不暴露给前端，避免前端拿到双重形态。
 * 逻辑删除由全局配置（{@code logic-delete-field: deleted}）处理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("rule_definition")
public class RuleDefinition {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 规则名称 */
    @TableField("name")
    private String name;

    /** 描述 */
    @TableField("description")
    private String description;

    /** 限定设备ID，NULL=该用户全部设备 */
    @TableField("device_id")
    private Long deviceId;

    /** 触发源：PROPERTY/EVENT */
    @TableField("source_type")
    private String sourceType;

    /** 属性标识符或事件标识符 */
    @TableField("identifier")
    private String identifier;

    /** 比较符：GT/GTE/LT/LTE/EQ/NE（EVENT 源可空） */
    @TableField("operator")
    private String operator;

    /** 比较阈值（文本，按物模型类型解释） */
    @TableField("threshold_value")
    private String thresholdValue;

    /** 事件类型过滤（EVENT 源可空） */
    @TableField("event_type")
    private String eventType;

    /** 动作：UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP */
    @TableField("action_type")
    private String actionType;

    /** 动作配置 JSON（按 action_type 解释），库中为文本；响应中由 {@link #actionConfigView} 以对象形态暴露 */
    @TableField("action_config")
    @JsonIgnore
    private String actionConfig;

    /** 触发冷却窗口（秒），0=不限制 */
    @TableField("cooldown_seconds")
    private Integer cooldownSeconds;

    /** 最近一次触发时间（由异步执行路径回填，仅供展示；冷却判定用进程内状态） */
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

    /** 返回给前端的已解析动作配置对象；非持久化字段（设计文档 §10.1）。JSON 键名与请求体一致为 {@code actionConfig}。 */
    @TableField(exist = false)
    @JsonProperty("actionConfig")
    private Object actionConfigView;
}