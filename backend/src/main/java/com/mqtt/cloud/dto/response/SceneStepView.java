package com.mqtt.cloud.dto.response;

import lombok.Data;

/**
 * 场景步骤视图（T-23 设计文档 §5.1 / §5.3）。
 * <p>
 * 返回给前端的**已解析对象**：库中 {@code target_config} / {@code action_config} 为 JSON 文本，
 * 服务层反序列化后放入 {@link #targetConfig} / {@link #actionConfig}。
 */
@Data
public class SceneStepView {

    /** 步骤ID。 */
    private Long id;

    /** 步骤序号，从 1 开始。 */
    private Integer seq;

    /** 本步骤执行前延时（秒）。 */
    private Integer delaySeconds;

    /** 步骤类型：UPDATE_PROPERTY / SEND_COMMAND / FORWARD_MQTT / FORWARD_HTTP / BRANCH / LOOP / PARALLEL / JOIN。 */
    private String actionType;

    /** 目标类型：TRIGGER / FIXED / NONE（分支 / 循环步骤无目标）。 */
    private String targetType;

    /** 固定目标集合（已解析对象）；仅 FIXED 时有值。 */
    private Object targetConfig;

    /** 动作配置（已解析对象，形状同 T-19 §5.2）。 */
    private Object actionConfig;

    /** 所属并行块起始步骤序号（T-49）。 */
    private Integer parallelSeq;

    /** 并行分支序号（T-49，0 起）。 */
    private Integer branchIndex;

    /** 是否启用：1/0。 */
    private Integer enabled;
}