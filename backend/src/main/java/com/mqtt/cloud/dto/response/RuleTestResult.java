package com.mqtt.cloud.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则试运行结果（T-19 设计文档 §10.2）。
 * <p>
 * 干跑结论 + 诊断信息：设备是否建模、标识符是否在物模型中定义、动作是否可执行，
 * 便于用户在保存前发现问题。**不产生任何副作用**（不落执行记录、不发送、不更新冷却锚点）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestResult {

    /** 条件是否命中 */
    private boolean matched;

    /** 命中 / 未命中原因说明 */
    private String reason;

    /** 该设备产品是否有物模型 */
    private boolean deviceModeled;

    /** 标识符是否在物模型中定义 */
    private boolean identifierModeled;

    /** 动作配置是否可执行 */
    private boolean actionExecutable;

    /** 动作摘要（可执行时为「将执行什么」，不可执行时为原因） */
    private String actionSummary;
}