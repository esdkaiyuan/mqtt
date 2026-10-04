package com.mqtt.cloud.dto.request;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 场景步骤请求（T-23 设计文档 §5.3 / §7.4）。
 * <p>
 * 动作流中的一环：{@code seq} + {@code delaySeconds} + 目标 + 动作配置。
 * {@code actionConfig} 形状与 T-19 §5.2 完全一致；{@code targetConfig} 仅在 {@code targetType=FIXED} 时有效。
 */
@Data
public class SceneStepRequest {

    /** 步骤序号，从 1 开始，连续无空洞。 */
    private Integer seq;

    /** 本步骤执行前延时（秒），相对前一步完成时刻（首步相对触发时刻）。 */
    private Integer delaySeconds;

    /** 动作类型：UPDATE_PROPERTY / SEND_COMMAND / FORWARD_MQTT / FORWARD_HTTP，必填。 */
    private String actionType;

    /** 目标类型：TRIGGER（触发设备）/ FIXED（固定目标集合），必填。 */
    private String targetType;

    /** 固定目标集合（BatchTargetRequest 形状）；仅 FIXED 时有效。 */
    private BatchTargetRequest targetConfig;

    /** 动作配置对象（按 actionType 解释，形状同 T-19 §5.2）。 */
    private JsonNode actionConfig;

    /** 是否启用，缺省 true。 */
    private Boolean enabled;
}