package com.mqtt.cloud.dto.request;

import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 规则创建 / 更新请求（T-19 设计文档 §7.1 / T-28 设计文档 §6）。
 * <p>
 * 字段均允许为空并在服务层集中校验：非法配置统一抛 {@code 6219}，非法动作类型抛 {@code 6220}，
 * 作用域非法抛 {@code 6269}，因此这里不做 Bean Validation 注解，避免校验错误码被全局处理器改写为 {@code 400}。
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

    /** 作用域档位：ALL / DEVICE / GROUP / TAG；为空时按 {@code deviceId} 是否为空推断。 */
    private String scopeType;

    /** 作用设备ID，{@code DEVICE} 档使用，其余档须为空；非空时必须属于当前用户。 */
    private Long deviceId;

    /** {@code GROUP} 档分组 ID 列表（含所有后代分组），其余档须为空；每项须属于当前用户。 */
    private List<Long> groupIds;

    /** {@code TAG} 档标签 ID 列表，其余档须为空；每项须属于当前用户。 */
    private List<Long> tagIds;

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

    /**
     * 多条件组合逻辑（T-33 / G-6-7）：{@code AND} / {@code OR}，缺省 {@code AND}。
     * <p>仅 {@link #conditions} 非空时有意义；单条件规则恒为 {@code AND}。
     */
    private String conditionLogic;

    /**
     * 多条件数组（T-33 / G-6-7），元素形如
     * {@code {"identifier","operator","thresholdValue","eventType"}}，最多 {@value com.mqtt.cloud.common.RuleConstants#MAX_CONDITIONS} 项。
     * <p>
     * 为空时使用 {@link #identifier} / {@link #operator} / {@link #thresholdValue} / {@link #eventType} 单条件；
     * 非空时单条件字段被忽略并由服务端取首个条件回填（保证列表筛选等仍读得到值）。
     */
    private List<JsonNode> conditions;

    /** 动作类型：UPDATE_PROPERTY / SEND_COMMAND / FORWARD_MQTT / FORWARD_HTTP，必填。 */
    private String actionType;

    /** 动作配置对象（按 actionType 解释）；{@link #actions} 非空时忽略。 */
    private JsonNode actionConfig;

    /**
     * 多动作数组（T-33 / G-6-7），元素形如 {@code {"actionType","actionConfig"}}，
     * 最多 {@value com.mqtt.cloud.common.RuleConstants#MAX_ACTIONS} 项。
     * <p>
     * 为空时使用 {@link #actionType} / {@link #actionConfig} 单动作；非空时这两个字段被忽略，
     * 服务端取首个动作回填（保持与旧列语义一致，便于存量读路径与列表展示）。
     */
    private List<JsonNode> actions;

    /** 触发冷却窗口（秒），缺省 0，取值 [0, 86400]。 */
    private Integer cooldownSeconds;

    /** 是否启用，缺省 true。 */
    private Boolean enabled;
}