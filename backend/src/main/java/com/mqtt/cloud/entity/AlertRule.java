package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则实体（T-17 设计文档 §6）。
 * <p>
 * 对应数据库表：alert_rule。三类来源：{@code THRESHOLD}（属性阈值）/
 * {@code OFFLINE}（设备离线）/ {@code EVENT}（事件上报）。
 * 逻辑删除由全局配置（{@code logic-delete-field: deleted}）处理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("alert_rule")
public class AlertRule {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 规则归属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 作用设备ID，NULL=该用户全部设备 */
    @TableField("device_id")
    private Long deviceId;

    /** 规则名称 */
    @TableField("name")
    private String name;

    /** 来源类型：THRESHOLD/OFFLINE/EVENT */
    @TableField("source_type")
    private String sourceType;

    /** 级别：INFO/WARNING/CRITICAL */
    @TableField("severity")
    private String severity;

    /** 属性/事件标识符（THRESHOLD/EVENT 必填） */
    @TableField("identifier")
    private String identifier;

    /** 比较符：GT/GTE/LT/LTE/EQ/NE（THRESHOLD 必填） */
    @TableField("operator")
    private String operator;

    /** 阈值（归一化文本，THRESHOLD 必填） */
    @TableField("threshold_value")
    private String thresholdValue;

    /** 窗口聚合方式：AVG/MIN/MAX/SUM/COUNT；窗口 0 时为 NULL（T-52） */
    @TableField("aggregate_type")
    private String aggregateType;

    /** 统计窗口（秒），0=实时最新值（T-52） */
    @TableField("window_seconds")
    private Integer windowSeconds;

    /** 窗口内最少样本数（T-52） */
    @TableField("min_samples")
    private Integer minSamples;

    /** 事件类型过滤：info/alert/fault（EVENT 可选） */
    @TableField("event_type")
    private String eventType;

    /** 离线持续阈值（秒，OFFLINE 用，0=立即） */
    @TableField("offline_seconds")
    private Integer offlineSeconds;

    /** 抑制窗口（秒，0=用全局默认） */
    @TableField("suppress_window_seconds")
    private Integer suppressWindowSeconds;

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

    /** 作用域档位：ALL / DEVICE / GROUP / TAG（T-28） */
    @TableField("scope_type")
    private String scopeType;

    /** GROUP 档分组 ID JSON 数组文本，其余档为 NULL；响应中由 {@link #groupIdsView} 以 ID 数组暴露 */
    @TableField("group_ids")
    @JsonIgnore
    private String groupIds;

    /** TAG 档标签 ID JSON 数组文本，其余档为 NULL；响应中由 {@link #tagIdsView} 以 ID 数组暴露 */
    @TableField("tag_ids")
    @JsonIgnore
    private String tagIds;

    /** 返回给前端的已解析分组 ID 数组；非持久化字段（T-28 设计文档 §4.2），JSON 键名为 {@code groupIds} */
    @TableField(exist = false)
    @JsonProperty("groupIds")
    private List<Long> groupIdsView;

    /** 返回给前端的已解析标签 ID 数组；非持久化字段（T-28 设计文档 §4.2），JSON 键名为 {@code tagIds} */
    @TableField(exist = false)
    @JsonProperty("tagIds")
    private List<Long> tagIdsView;
}
