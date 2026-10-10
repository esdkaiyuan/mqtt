package com.mqtt.cloud.service.scope;

import com.mqtt.cloud.common.AlertConstants;

import java.util.Collections;

/**
 * 作用域四档匹配纯函数（T-28 设计文档 §4.3）。
 * <p>
 * 无状态、无依赖，供规则推评估与告警推评估共用；匹配只做内存集合运算，不查库、不缓存。
 * 档位字面量与 {@code AlertConstants} / {@code RuleConstants} 中同名常量取值一致 —— 两个域各自持有
 * 一份以保持解耦，本类取用共享的 {@link AlertConstants} 一份。
 */
public final class ScopeMatcher {

    private ScopeMatcher() {
    }

    /**
     * 判定某设备是否落在作用域内。
     *
     * @param scope       作用域；为空或档位为空时不命中
     * @param deviceId    待判定设备 ID
     * @param memberships 该设备的隶属集合；为空按无隶属处理（仅 {@code ALL} 仍命中）
     * @return 命中返回 {@code true}
     */
    public static boolean matches(RuleScope scope, Long deviceId, DeviceMemberships memberships) {
        if (scope == null || scope.scopeType() == null) {
            return false;
        }
        DeviceMemberships resolved = memberships == null ? DeviceMemberships.empty() : memberships;
        return switch (scope.scopeType()) {
            case AlertConstants.SCOPE_ALL -> true;
            case AlertConstants.SCOPE_DEVICE -> scope.deviceId() != null && scope.deviceId().equals(deviceId);
            case AlertConstants.SCOPE_GROUP -> !Collections.disjoint(scope.groupIds(), resolved.groupIds());
            case AlertConstants.SCOPE_TAG -> !Collections.disjoint(scope.tagIds(), resolved.tagIds());
            default -> false;
        };
    }
}