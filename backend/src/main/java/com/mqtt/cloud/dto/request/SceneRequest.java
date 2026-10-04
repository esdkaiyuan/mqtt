package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 场景创建 / 更新请求（T-23 设计文档 §7.1）。
 * <p>
 * 字段均允许为空并在服务层集中校验：非法配置统一抛 {@code 6241}，非法动作类型抛 {@code 6242}，
 * 非法触发源抛 {@code 6245}，超上限抛 {@code 6244}，越权设备抛 {@code 2003}，
 * 因此这里不做 Bean Validation 注解，避免校验错误码被全局处理器改写为 {@code 400}。
 * <p>
 * 触发源语义见 §5.2：{@code PROPERTY} / {@code EVENT} 需 {@code triggerIdentifier}；
 * {@code TIMER} 需 {@code timerCron} 且触发设备 / 标识符必须为空。
 */
@Data
public class SceneRequest {

    /** 场景名称，非空，长度 ≤ 64，同用户下不重名。 */
    private String name;

    /** 描述，可空，长度 ≤ 255。 */
    private String description;

    /** 触发源：PROPERTY / EVENT / TIMER，必填。 */
    private String triggerType;

    /** 触发限定设备ID，可空表示该用户全部设备；非空时必须属于当前用户。TIMER 源必须为空。 */
    private Long triggerDeviceId;

    /** 触发属性 / 事件标识符；PROPERTY / EVENT 必填，TIMER 必须为空。 */
    private String triggerIdentifier;

    /** 触发比较符：GT / GTE / LT / LTE / EQ / NE；PROPERTY 源可空（空=任意上报即触发）。 */
    private String triggerOperator;

    /** 触发比较阈值（文本）；与 {@code triggerOperator} 同时为空或同时非空。 */
    private String triggerThreshold;

    /** 事件类型过滤（info / alert / fault），EVENT 源可空。 */
    private String triggerEventType;

    /** 定时触发 cron（5 字段，分钟级），仅 TIMER 源允许非空。 */
    private String timerCron;

    /** 条件组合：AND / OR，缺省 AND；条件组为空时忽略。 */
    private String conditionLogic;

    /** 附加条件项列表，可空（空 = 无附加条件）。 */
    private List<SceneConditionRequest> conditions;

    /** 触发冷却窗口（秒），缺省 0，取值 [0, 86400]。 */
    private Integer cooldownSeconds;

    /** 是否启用，缺省 true。 */
    private Boolean enabled;

    /** 动作流步骤列表，非空，长度 1..max-steps-per-scene，{@code seq} 连续且从 1 开始。 */
    private List<SceneStepRequest> steps;
}