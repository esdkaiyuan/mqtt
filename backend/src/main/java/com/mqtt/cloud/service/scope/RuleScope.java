package com.mqtt.cloud.service.scope;

import java.util.List;

/**
 * 规则 / 告警作用域的不可变值对象（T-28 设计文档 §4.2）。
 * <p>
 * 四档判别由 {@code scopeType} 承担，同一时刻只有一档的数据字段非空；{@code groupIds} / {@code tagIds}
 * 为已解析的 ID 列表（非库中 JSON 文本），构造时做防御性拷贝，避免调用方后续修改。
 * <p>
 * 该值对象被规则推评估、告警推评估、离线巡检三条路径共用，构造与解析由服务层负责。
 *
 * @param scopeType 档位：{@code ALL} / {@code DEVICE} / {@code GROUP} / {@code TAG}，可空（由服务层推断）
 * @param deviceId  {@code DEVICE} 档设备 ID，其余档为空
 * @param groupIds  {@code GROUP} 档分组 ID 列表，其余档为空
 * @param tagIds    {@code TAG} 档标签 ID 列表，其余档为空
 */
public record RuleScope(String scopeType, Long deviceId, List<Long> groupIds, List<Long> tagIds) {

    public RuleScope {
        groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    }

    /** 由四要素构造，空集合归一为不可变空列表。 */
    public static RuleScope of(String scopeType, Long deviceId, List<Long> groupIds, List<Long> tagIds) {
        return new RuleScope(scopeType, deviceId, groupIds, tagIds);
    }
}