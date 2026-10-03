package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 规则试运行请求（T-19 设计文档 §7.4）。
 * <p>
 * 用给定的设备 / 标识符 / 取值判定条件是否命中，返回动作摘要但**不产生任何副作用**。
 */
@Data
public class RuleTestRequest {

    /** 试运行设备ID，非空且必须属于当前用户。 */
    private Long deviceId;

    /** 待判定标识符，非空，长度 ≤ 64。 */
    private String identifier;

    /** 待判定取值文本；数值比较符下必须是可解析的十进制数，可空（EQ / NE 可为文本）。 */
    private String valueText;
}