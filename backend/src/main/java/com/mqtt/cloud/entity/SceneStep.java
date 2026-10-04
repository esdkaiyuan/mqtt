package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 场景动作流步骤实体（T-23 设计文档 §6.2）。
 * <p>
 * 对应数据库表：scene_step。一个场景的步骤按 {@code seq} 升序执行；{@code delaySeconds} 表示
 * 「相对前一步完成时刻」的延时。{@code targetType} 决定动作作用对象（{@code TRIGGER} 触发设备 /
 * {@code FIXED} 固定目标集合），{@code targetConfig} 仅在 {@code FIXED} 时有效（{@code BatchTargetRequest} 形状）。
 * <p>
 * 步骤**无逻辑删除列**：场景更新采用「整体替换步骤」（先删旧行再插新行），故本表不参与全局逻辑删除。
 * {@code targetConfig} / {@code actionConfig} 在库中为 JSON 文本，返回前端时由服务层反序列化为对象。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("scene_step")
public class SceneStep {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 场景ID */
    @TableField("scene_id")
    private Long sceneId;

    /** 步骤序号，从 1 开始 */
    @TableField("seq")
    private Integer seq;

    /** 本步骤执行前延时（秒），相对前一步完成时刻 */
    @TableField("delay_seconds")
    private Integer delaySeconds;

    /** 动作：UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP */
    @TableField("action_type")
    private String actionType;

    /** 目标：TRIGGER=触发设备 / FIXED=固定目标集合 */
    @TableField("target_type")
    private String targetType;

    /** 固定目标 JSON（BatchTargetRequest 形状），库中为文本；仅 FIXED 时有效 */
    @TableField("target_config")
    @JsonIgnore
    private String targetConfig;

    /** 动作配置 JSON（形状同 T-19 §5.2），库中为文本 */
    @TableField("action_config")
    @JsonIgnore
    private String actionConfig;

    /** 是否启用：1/0 */
    @TableField("enabled")
    private Integer enabled;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}