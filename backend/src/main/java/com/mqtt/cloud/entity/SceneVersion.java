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
 * 场景版本快照实体（T-51）。
 * <p>
 * 每次创建 / 更新 / 回滚保存一份完整快照；只追加，不修改历史行。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("scene_version")
public class SceneVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("version_no")
    private Integer versionNo;

    /** CREATE / UPDATE / RESTORE */
    @TableField("change_type")
    private String changeType;

    @TableField("scene_name")
    private String sceneName;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("step_count")
    private Integer stepCount;

    /** 完整快照 JSON（SceneSnapshot） */
    @TableField("snapshot")
    private String snapshot;

    @TableField("operator_id")
    private Long operatorId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
