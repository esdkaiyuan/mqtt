package com.mqtt.cloud.dto.request;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 规则创建 / 更新请求（T-19 设计文档 §7.1）。
 * <p>
 * 字段均允许为空并在服务层集中校验：非法配置统一抛 {@code 6219}，非法动作类型抛 {@code 6220}，
 * 因此这里不做 Bean Validation 注解，避免校验错误码被全局处理器改写为 {@code 400}。
 * <ul>
 *   <li>{@code PROPERTY}：{@code identifier} / {@code operator} / {@code thresholdValue} 必填，{@code eventType} 必须为空</li>
 *   <li>{@code EVENT}：{@code identifier} 必填，{@code operator} / {@code thresholdValue} 必须为空，{@code eventType} 可选</li>
 * </ul>
 * {@code actionConfig} 为对象（{@link JsonNode}），服务层负责与库中 {@code TEXT} 的序列化 / 反序列化。
 */
@Data
public class RuleRequest {

    /** 规则名称，非空，长度 ≤ 64。 */
    private String name;

    /** 描述，可空，长度 ≤ 255。 */
    private String description;

    /** 作用设备ID，可空表示全部设备；非空时必须属于当前用户。 */
    private Long deviceId;

    /** 触发源：PROPERTY / EVENT，必填。 */
    private String sourceType;

    /** 属性标识符或事件标识符，非空，长度 ≤ 64。 */
    private String identifier;

    /** 比较符：GT / GTE / LT / LTE / EQ / NE；PROPERTY 必填，EVENT 必须为空。 */
    private String operator;

    /** 比较阈值（文本）；PROPERTY 必填，数值比较符下必须是可解析的十进制数。 */
    private String thresholdValue;

    /** 事件类型过滤（info / alert / fault），EVENT 可选，长度 ≤ 16。 */
    private String eventType;

    /** 动作类型：UPDATE_PROPERTY / SEND_COMMAND / FORWARD_MQTT / FORWARD_HTTP，必填。 */
    private String actionType;

    /** 动作配置对象（按 actionType 解释）。 */
    private JsonNode actionConfig;

    /** 触发冷却窗口（秒），缺省 0，取值 [0, 86400]。 */
    private Integer cooldownSeconds;

    /** 是否启用，缺省 true。 */
    private Boolean enabled;
}