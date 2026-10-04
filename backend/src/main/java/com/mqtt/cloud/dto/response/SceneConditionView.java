package com.mqtt.cloud.dto.response;

import lombok.Data;

/**
 * 场景条件项视图（T-23 设计文档 §5.2）。
 * <p>
 * 返回给前端的**已解析对象**（库中 {@code condition_config} 为 JSON 文本）。
 */
@Data
public class SceneConditionView {

    /** 条件取值设备ID，空表示取触发设备。 */
    private Long deviceId;

    /** 属性标识符。 */
    private String identifier;

    /** 比较符：GT / GTE / LT / LTE / EQ / NE。 */
    private String operator;

    /** 比较阈值（文本）。 */
    private String threshold;
}