package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 场景条件项请求（T-23 设计文档 §5.2 / §7.3）。
 * <p>
 * 附加条件项：目标设备（缺省取触发设备）+ 标识符 + 比较符 + 阈值。
 * {@code TIMER} 源下无触发设备，{@code deviceId} 必填。
 */
@Data
public class SceneConditionRequest {

    /** 条件取值设备ID，可空表示取触发设备（TIMER 源下必填）；非空时必须属于当前用户。 */
    private Long deviceId;

    /** 属性标识符，非空，长度 ≤ 64。 */
    private String identifier;

    /** 比较符：GT / GTE / LT / LTE / EQ / NE，必填。 */
    private String operator;

    /** 比较阈值（文本）；数值比较符下必须是可解析的十进制数。 */
    private String threshold;
}