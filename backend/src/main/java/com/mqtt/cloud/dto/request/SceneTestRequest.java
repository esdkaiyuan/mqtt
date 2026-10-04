package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 场景试运行请求（T-23 设计文档 §7.5 / §10.2）。
 * <p>
 * 用给定的设备 / 标识符 / 取值判定触发与条件组是否命中，返回步骤摘要但**不产生任何副作用**
 * （不落库、不投递、不占用冷却锚点）。{@code TIMER} 源可省略 {@code deviceId} / {@code identifier} / {@code value}。
 */
@Data
public class SceneTestRequest {

    /** 试运行设备ID，可空（TIMER 源可省略）；非空时必须属于当前用户。 */
    private Long deviceId;

    /** 待判定标识符，可空（TIMER 源可省略）。 */
    private String identifier;

    /** 待判定取值文本，可空（EQ / NE 可为文本；数值比较符下必须是可解析的十进制数）。 */
    private String value;
}