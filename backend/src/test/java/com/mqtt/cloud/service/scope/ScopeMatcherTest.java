package com.mqtt.cloud.service.scope;

import com.mqtt.cloud.common.AlertConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 作用域匹配纯函数单测（T-28 实施计划 P2 / 设计文档 §4.3）。
 * <p>
 * 覆盖四档匹配矩阵与空集合边界：{@code ALL} 恒命中、{@code DEVICE} 相等、{@code GROUP} /
 * {@code TAG} 交集命中、作用域空集合与未知档位一律不命中。
 */
class ScopeMatcherTest {

    private static final Long DEVICE_ID = 100L;

    @Test
    void all_scope_always_matches() {
        RuleScope scope = RuleScope.of(AlertConstants.SCOPE_ALL, null, null, null);

        assertThat(ScopeMatcher.matches(scope, DEVICE_ID, DeviceMemberships.empty())).isTrue();
    }

    @Test
    void device_scope_matches_only_the_same_device() {
        RuleScope scope = RuleScope.of(AlertConstants.SCOPE_DEVICE, DEVICE_ID, null, null);

        assertThat(ScopeMatcher.matches(scope, DEVICE_ID, DeviceMemberships.empty())).isTrue();
        assertThat(ScopeMatcher.matches(scope, 200L, DeviceMemberships.empty())).isFalse();
    }

    @Test
    void group_scope_matches_when_membership_intersects() {
        RuleScope scope = RuleScope.of(AlertConstants.SCOPE_GROUP, null, List.of(7L, 8L), null);
        DeviceMemberships hit = new DeviceMemberships(Set.of(8L, 9L), Set.of());
        DeviceMemberships miss = new DeviceMemberships(Set.of(1L, 2L), Set.of());

        assertThat(ScopeMatcher.matches(scope, DEVICE_ID, hit)).isTrue();
        assertThat(ScopeMatcher.matches(scope, DEVICE_ID, miss)).isFalse();
    }

    @Test
    void tag_scope_matches_when_membership_intersects() {
        RuleScope scope = RuleScope.of(AlertConstants.SCOPE_TAG, null, null, List.of(5L));
        DeviceMemberships hit = new DeviceMemberships(Set.of(), Set.of(5L, 6L));
        DeviceMemberships miss = new DeviceMemberships(Set.of(), Set.of(6L));

        assertThat(ScopeMatcher.matches(scope, DEVICE_ID, hit)).isTrue();
        assertThat(ScopeMatcher.matches(scope, DEVICE_ID, miss)).isFalse();
    }

    @Test
    void group_and_tag_scope_miss_when_scope_is_empty() {
        RuleScope group = RuleScope.of(AlertConstants.SCOPE_GROUP, null, List.of(), null);
        RuleScope tag = RuleScope.of(AlertConstants.SCOPE_TAG, null, null, List.of());
        DeviceMemberships memberships = new DeviceMemberships(Set.of(8L), Set.of(5L));

        assertThat(ScopeMatcher.matches(group, DEVICE_ID, memberships)).isFalse();
        assertThat(ScopeMatcher.matches(tag, DEVICE_ID, memberships)).isFalse();
    }

    @Test
    void unknown_or_null_scope_never_matches() {
        assertThat(ScopeMatcher.matches(RuleScope.of("WEIRD", null, null, null),
                DEVICE_ID, DeviceMemberships.empty())).isFalse();
        assertThat(ScopeMatcher.matches(RuleScope.of(null, null, null, null),
                DEVICE_ID, DeviceMemberships.empty())).isFalse();
        assertThat(ScopeMatcher.matches(null, DEVICE_ID, DeviceMemberships.empty())).isFalse();
    }

    @Test
    void all_scope_matches_even_with_null_memberships() {
        RuleScope scope = RuleScope.of(AlertConstants.SCOPE_ALL, null, null, null);

        assertThat(ScopeMatcher.matches(scope, DEVICE_ID, null)).isTrue();
    }
}