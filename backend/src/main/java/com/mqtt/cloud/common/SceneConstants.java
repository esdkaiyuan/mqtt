package com.mqtt.cloud.common;

import java.util.Set;

/**
 * 场景联动共享常量（T-23 设计文档 §3 / §5 / §8）。
 * <p>
 * 集中触发源 / 动作 / 目标 / 条件逻辑 / 执行与步骤状态 / 触发方式 / 占位符的字符串口径，
 * 避免评估、服务、动作执行、模板渲染各处散落字面量而产生漂移。
 * <p>
 * 比较符与动作配置形状**复用 {@link RuleConstants}**（与 T-19 语义一致），此处不重复定义，
 * 仅以 {@link #NUMERIC_OPERATORS} 别名暴露，保持场景域代码可读。
 */
public final class SceneConstants {

    private SceneConstants() {
    }

    // ---------- 触发源 ----------

    /** 触发源：属性上报。 */
    public static final String TRIGGER_PROPERTY = "PROPERTY";
    /** 触发源：事件上报。 */
    public static final String TRIGGER_EVENT = "EVENT";
    /** 触发源：定时（cron）。 */
    public static final String TRIGGER_TIMER = "TIMER";

    /** 全部触发源类型。 */
    public static final Set<String> TRIGGERS = Set.of(TRIGGER_PROPERTY, TRIGGER_EVENT, TRIGGER_TIMER);

    // ---------- 动作类型 ----------

    /** 动作：更新云端属性。 */
    public static final String ACTION_UPDATE_PROPERTY = RuleConstants.ACTION_UPDATE_PROPERTY;
    /** 动作：下发命令。 */
    public static final String ACTION_SEND_COMMAND = RuleConstants.ACTION_SEND_COMMAND;
    /** 动作：转发外部 MQTT。 */
    public static final String ACTION_FORWARD_MQTT = RuleConstants.ACTION_FORWARD_MQTT;
    /** 动作：转发 HTTP。 */
    public static final String ACTION_FORWARD_HTTP = RuleConstants.ACTION_FORWARD_HTTP;

    /** 全部动作类型。 */
    public static final Set<String> ACTIONS = RuleConstants.ACTIONS;

    /** 转发类动作（仅允许 {@code TRIGGER} 目标，单次出站）。 */
    public static final Set<String> FORWARD_ACTIONS = Set.of(ACTION_FORWARD_MQTT, ACTION_FORWARD_HTTP);

    // ---------- 步骤目标类型 ----------

    /** 目标：触发设备（单台）。 */
    public static final String TARGET_TRIGGER = "TRIGGER";
    /** 目标：固定目标集合（手选 ∪ 产品 ∪ 分组 ∪ 标签，多台）。 */
    public static final String TARGET_FIXED = "FIXED";

    /** 全部目标类型。 */
    public static final Set<String> TARGETS = Set.of(TARGET_TRIGGER, TARGET_FIXED);

    // ---------- 条件逻辑 ----------

    /** 条件组：全部满足。 */
    public static final String LOGIC_AND = "AND";
    /** 条件组：任一满足。 */
    public static final String LOGIC_OR = "OR";

    /** 全部条件逻辑。 */
    public static final Set<String> LOGICS = Set.of(LOGIC_AND, LOGIC_OR);

    // ---------- 执行记录状态 ----------

    /** 执行状态：已触发待首步。 */
    public static final String EXEC_PENDING = "PENDING";
    /** 执行状态：有步骤执行中。 */
    public static final String EXEC_RUNNING = "RUNNING";
    /** 执行状态：成功（终态）。 */
    public static final String EXEC_SUCCESS = "SUCCESS";
    /** 执行状态：失败（终态，中止后续）。 */
    public static final String EXEC_FAILED = "FAILED";

    /** 全部执行状态。 */
    public static final Set<String> EXEC_STATUSES = Set.of(EXEC_PENDING, EXEC_RUNNING, EXEC_SUCCESS, EXEC_FAILED);

    // ---------- 步骤执行状态 ----------

    /** 步骤状态：待执行 / 重试中。 */
    public static final String STEP_PENDING = "PENDING";
    /** 步骤状态：执行中（抢占成功）。 */
    public static final String STEP_RUNNING = "RUNNING";
    /** 步骤状态：成功（终态）。 */
    public static final String STEP_SUCCESS = "SUCCESS";
    /** 步骤状态：失败（终态，重试耗尽）。 */
    public static final String STEP_FAILED = "FAILED";
    /** 步骤状态：已跳过（中止时后续未执行步骤）。 */
    public static final String STEP_SKIPPED = "SKIPPED";

    /** 全部步骤状态。 */
    public static final Set<String> STEP_STATUSES =
            Set.of(STEP_PENDING, STEP_RUNNING, STEP_SUCCESS, STEP_FAILED, STEP_SKIPPED);

    // ---------- 触发方式 ----------

    /** 触发方式：自动（上行样本 / 定时）。 */
    public static final String SOURCE_AUTO = "AUTO";
    /** 触发方式：手动执行。 */
    public static final String SOURCE_MANUAL = "MANUAL";

    // ---------- 比较符（复用 T-19 口径） ----------

    /** 数值比较符：两侧需可解析为十进制数，按 {@code BigDecimal.compareTo} 比较。 */
    public static final Set<String> NUMERIC_OPERATORS = RuleConstants.NUMERIC_OPERATORS;

    // ---------- Webhook 事件类型 ----------

    /** Webhook 事件类型：场景触发（转发 HTTP 请求头 {@code X-Event-Type}）。 */
    public static final String EVENT_SCENE_TRIGGERED = "scene.triggered";

    /** 转发 HTTP 请求头：场景 ID。 */
    public static final String HEADER_SCENE_ID = "X-Scene-Id";

    // ---------- 模板占位符（在 T-19 基础上新增场景维度） ----------

    public static final String PLACEHOLDER_SCENE_ID = "${sceneId}";
    public static final String PLACEHOLDER_SCENE_NAME = "${sceneName}";
    public static final String PLACEHOLDER_EXECUTION_ID = "${executionId}";
    public static final String PLACEHOLDER_STEP_SEQ = "${stepSeq}";
    public static final String PLACEHOLDER_TRIGGER_TYPE = "${triggerType}";
    public static final String PLACEHOLDER_TRIGGER_DEVICE_ID = "${triggerDeviceId}";
    public static final String PLACEHOLDER_TRIGGER_DEVICE_KEY = "${triggerDeviceKey}";
    public static final String PLACEHOLDER_TRIGGER_DEVICE_NAME = "${triggerDeviceName}";
    public static final String PLACEHOLDER_TRIGGER_DEVICE_TYPE = "${triggerDeviceType}";
    public static final String PLACEHOLDER_TRIGGER_IDENTIFIER = "${triggerIdentifier}";
    public static final String PLACEHOLDER_TRIGGER_VALUE = "${triggerValue}";
    public static final String PLACEHOLDER_TRIGGERED_AT = "${triggeredAt}";
    public static final String PLACEHOLDER_TARGET_DEVICE_ID = "${targetDeviceId}";
    public static final String PLACEHOLDER_TARGET_DEVICE_KEY = "${targetDeviceKey}";
    public static final String PLACEHOLDER_TARGET_DEVICE_NAME = "${targetDeviceName}";
    public static final String PLACEHOLDER_TARGET_DEVICE_TYPE = "${targetDeviceType}";
    public static final String PLACEHOLDER_TIMESTAMP = "${timestamp}";

    /** 全部场景模板占位符。 */
    public static final Set<String> PLACEHOLDERS = Set.of(
            PLACEHOLDER_SCENE_ID, PLACEHOLDER_SCENE_NAME, PLACEHOLDER_EXECUTION_ID, PLACEHOLDER_STEP_SEQ,
            PLACEHOLDER_TRIGGER_TYPE, PLACEHOLDER_TRIGGER_DEVICE_ID, PLACEHOLDER_TRIGGER_DEVICE_KEY,
            PLACEHOLDER_TRIGGER_DEVICE_NAME, PLACEHOLDER_TRIGGER_DEVICE_TYPE, PLACEHOLDER_TRIGGER_IDENTIFIER,
            PLACEHOLDER_TRIGGER_VALUE, PLACEHOLDER_TRIGGERED_AT, PLACEHOLDER_TARGET_DEVICE_ID,
            PLACEHOLDER_TARGET_DEVICE_KEY, PLACEHOLDER_TARGET_DEVICE_NAME, PLACEHOLDER_TARGET_DEVICE_TYPE,
            PLACEHOLDER_TIMESTAMP);

    // ---------- 上限常量（运行时可被 app.scene.* 覆盖的见 SceneProperties） ----------

    /** 单用户场景数上限（默认值，运行时可被 {@code app.scene.max-scenes-per-user} 覆盖）。 */
    public static final int MAX_SCENES_PER_USER = 100;
    /** 单场景步骤数上限（默认值，运行时可被 {@code app.scene.max-steps-per-scene} 覆盖）。 */
    public static final int MAX_STEPS_PER_SCENE = 20;
    /** 单场景条件项数上限（默认值，运行时可被 {@code app.scene.max-conditions-per-scene} 覆盖）。 */
    public static final int MAX_CONDITIONS_PER_SCENE = 10;
    /** 单步骤最大延时（秒，默认值，运行时可被 {@code app.scene.max-step-delay-seconds} 覆盖）。 */
    public static final int MAX_STEP_DELAY_SECONDS = 86400;
}