package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个「设备 × 属性」序列（T-21 设计文档 §6.1）。
 * <p>
 * <b>只读投影，非表实体</b>。无数据的组合仍会返回本对象（{@code points} 为空列表），
 * 便于前端区分「无数据」与「未请求」。
 */
@Data
public class PropertySeriesVO {

    private Long deviceId;

    private String identifier;

    /** 物模型数据类型（int / float / double / bool / enum / text / ...）。 */
    private String dataType;

    /** 是否为可聚合数值型：{@code true} 时 {@code points} 含 min / max / avg，否则三者恒为 null。 */
    private boolean numeric;

    /** 桶序列，按时间升序；无数据时为空列表。 */
    private List<PropertyHistoryPoint> points = new ArrayList<>();
}
