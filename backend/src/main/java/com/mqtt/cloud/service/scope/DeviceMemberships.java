package com.mqtt.cloud.service.scope;

import java.util.Set;

/**
 * 一台设备的隶属集合（T-28 设计文档 §4.3）。
 * <p>
 * {@code groupIds} 为设备所属分组**及其全部祖先分组**的 ID（上溯后），{@code tagIds} 为设备标签 ID。
 * 推评估时按设备解析一次、供该次调用内全部规则复用；构造时做防御性拷贝。
 *
 * @param groupIds 设备所属分组及祖先分组 ID 集合
 * @param tagIds   设备标签 ID 集合
 */
public record DeviceMemberships(Set<Long> groupIds, Set<Long> tagIds) {

    public DeviceMemberships {
        groupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
        tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
    }

    /** 无任何分组 / 标签隶属的空集合。 */
    public static DeviceMemberships empty() {
        return new DeviceMemberships(Set.of(), Set.of());
    }
}