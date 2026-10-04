package com.mqtt.cloud.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景试运行结果（T-23 设计文档 §10.2）。
 * <p>
 * 干跑结论 + 诊断信息：触发是否命中、条件组是否满足、是否被冷却拦截，以及步骤摘要。
 * **不产生任何副作用**（不落库、不投递、不占用冷却锚点）。
 * <p>
 * {@code triggerMatched=false} 时 {@code conditions} 与 {@code stepSummaries} 返回空数组；
 * {@code conditionMatched=false} 时仍返回各项 {@code actualValue} 便于排障。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneTestResult {

    /** 触发源是否命中（来源 / 作用域 / 标识符 / 触发条件）。 */
    private boolean triggerMatched;

    /** 条件组是否满足（无附加条件视为满足）。 */
    private boolean conditionMatched;

    /** 是否被冷却窗口拦截（试运行不占用锚点，仅报告当前进程内锚点状态）。 */
    private boolean cooldownBlocked;

    /** 条件项逐项判定结果。 */
    private List<ConditionEvaluation> conditions = new ArrayList<>();

    /** 步骤摘要。 */
    private List<StepSummary> stepSummaries = new ArrayList<>();

    /** 预计总耗时（秒）：各步骤延时之和（触发到末步开始的最短时间）。 */
    private int estimatedDurationSeconds;

    /** 条件项判定明细。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionEvaluation {

        /** 属性标识符。 */
        private String identifier;

        /** 比较符。 */
        private String operator;

        /** 比较阈值（文本）。 */
        private String threshold;

        /** 实际取值（设备无该属性最新值时为空）。 */
        private String actualValue;

        /** 是否满足。 */
        private boolean satisfied;
    }

    /** 步骤摘要。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepSummary {

        /** 步骤序号。 */
        private Integer seq;

        /** 动作类型。 */
        private String actionType;

        /** 目标类型。 */
        private String targetType;

        /** 目标设备数（TRIGGER 单台为 1；FORWARD_* 为 0）。 */
        private int targetDeviceCount;

        /** 本步骤延时（秒）。 */
        private Integer delaySeconds;

        /** 可读摘要。 */
        private String summary;
    }
}