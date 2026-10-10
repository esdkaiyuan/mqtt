package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.ScopeProperties;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.entity.DeviceTag;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceTagService;
import com.mqtt.cloud.service.scope.DeviceMemberships;
import com.mqtt.cloud.service.scope.RuleScope;
import com.mqtt.cloud.util.ScopeJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 作用域解析服务单测（T-28 实施计划 P2 / 设计文档 §4.3 / §4.4 / §6）。
 * <p>
 * 覆盖两个方向：设备 → 隶属（直接分组 + 祖先上溯、标签），作用域 → 设备集合（子分组展开、去重、
 * {@code ALL} 空集语义）；以及校验与规范化（档位一致性、归属、数量上限、缺省推断、错误码透传）。
 */
class DeviceScopeServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long DEVICE_ID = 100L;
    private static final ResultCode INVALID = ResultCode.ALERT_RULE_SCOPE_INVALID;

    private DeviceGroupService deviceGroupService;
    private DeviceTagService deviceTagService;
    private ScopeProperties scopeProperties;
    private DeviceScopeServiceImpl service;

    @BeforeEach
    void setUp() {
        deviceGroupService = mock(DeviceGroupService.class);
        deviceTagService = mock(DeviceTagService.class);
        scopeProperties = new ScopeProperties();
        service = new DeviceScopeServiceImpl(deviceGroupService, deviceTagService, scopeProperties,
                new ScopeJson(new ObjectMapper()));
    }

    // ---------- 持久化字段 → 作用域值对象 ----------

    @Test
    void toScope_should_parse_json_ids_and_infer_type() {
        RuleScope scope = service.toScope(AlertConstants.SCOPE_GROUP, null, "[5,5,7]", null);

        assertThat(scope.scopeType()).isEqualTo(AlertConstants.SCOPE_GROUP);
        assertThat(scope.groupIds()).containsExactly(5L, 7L);
        assertThat(scope.tagIds()).isEmpty();
    }

    @Test
    void toScope_should_infer_all_and_device_when_type_blank() {
        assertThat(service.toScope(null, null, null, null).scopeType()).isEqualTo(AlertConstants.SCOPE_ALL);
        assertThat(service.toScope("  ", DEVICE_ID, null, null).scopeType())
                .isEqualTo(AlertConstants.SCOPE_DEVICE);
    }

    @Test
    void toScope_should_keep_unknown_type_without_throwing() {
        RuleScope scope = service.toScope("WEIRD", null, "not-json", null);

        assertThat(scope.scopeType()).isEqualTo("WEIRD");
        assertThat(scope.groupIds()).isEmpty();
    }

    // ---------- 设备 → 隶属 ----------

    @Test
    void memberships_should_include_direct_groups_and_all_ancestors() {
        when(deviceGroupService.groupsByDeviceIds(any()))
                .thenReturn(Map.of(DEVICE_ID, List.of(group(5L, 3L))));
        when(deviceGroupService.list(USER_ID))
                .thenReturn(List.of(group(5L, 3L), group(3L, 1L), group(1L, null), group(99L, null)));
        when(deviceTagService.tagsByDeviceIds(any()))
                .thenReturn(Map.of(DEVICE_ID, List.of(tag(9L), tag(10L))));

        DeviceMemberships memberships = service.memberships(USER_ID, DEVICE_ID);

        assertThat(memberships.groupIds()).containsExactlyInAnyOrder(5L, 3L, 1L);
        assertThat(memberships.tagIds()).containsExactlyInAnyOrder(9L, 10L);
    }

    @Test
    void memberships_should_be_empty_when_device_has_no_group_or_tag() {
        when(deviceGroupService.groupsByDeviceIds(any())).thenReturn(Map.of());
        when(deviceTagService.tagsByDeviceIds(any())).thenReturn(Map.of());

        DeviceMemberships memberships = service.memberships(USER_ID, DEVICE_ID);

        assertThat(memberships.groupIds()).isEmpty();
        assertThat(memberships.tagIds()).isEmpty();
    }

    @Test
    void memberships_should_be_empty_and_touch_nothing_for_null_device() {
        DeviceMemberships memberships = service.memberships(USER_ID, null);

        assertThat(memberships.groupIds()).isEmpty();
        assertThat(memberships.tagIds()).isEmpty();
        verifyNoInteractions(deviceGroupService, deviceTagService);
    }

    // ---------- 作用域 → 设备集合 ----------

    @Test
    void deviceIds_all_should_be_empty_meaning_unrestricted() {
        assertThat(service.deviceIds(USER_ID, RuleScope.of(AlertConstants.SCOPE_ALL, null, null, null)))
                .isEmpty();
    }

    @Test
    void deviceIds_device_should_be_single_element() {
        assertThat(service.deviceIds(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_DEVICE, DEVICE_ID, null, null)))
                .containsExactly(DEVICE_ID);
    }

    @Test
    void deviceIds_group_should_expand_descendants_and_dedupe() {
        when(deviceGroupService.descendantIds(USER_ID, 5L)).thenReturn(List.of(5L, 6L));
        when(deviceGroupService.descendantIds(USER_ID, 7L)).thenReturn(List.of(7L, 8L));
        when(deviceGroupService.deviceIdsByGroupIds(any())).thenReturn(List.of(100L, 101L, 100L));

        List<Long> deviceIds = service.deviceIds(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_GROUP, null, List.of(5L, 7L), null));

        assertThat(deviceIds).containsExactly(100L, 101L);
        verify(deviceGroupService).deviceIdsByGroupIds(eq(Set.of(5L, 6L, 7L, 8L)));
    }

    @Test
    void deviceIds_tag_should_dedupe_and_skip_empty_scope() {
        when(deviceTagService.deviceIdsByTagIds(any())).thenReturn(List.of(100L, 100L, 102L));

        assertThat(service.deviceIds(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_TAG, null, null, List.of(9L, 10L))))
                .containsExactly(100L, 102L);
        assertThat(service.deviceIds(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_TAG, null, null, List.of())))
                .isEmpty();
    }

    // ---------- 校验与规范化 ----------

    @Test
    void validate_all_should_drop_everything() {
        RuleScope normalized = service.validateAndNormalize(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_ALL, null, null, null), INVALID);

        assertThat(normalized.scopeType()).isEqualTo(AlertConstants.SCOPE_ALL);
        assertThat(normalized.deviceId()).isNull();
        assertThat(normalized.groupIds()).isEmpty();
        assertThat(normalized.tagIds()).isEmpty();
    }

    @Test
    void validate_group_should_dedupe_check_ownership_and_normalize() {
        when(deviceGroupService.getOwned(USER_ID, 5L)).thenReturn(group(5L, null));
        when(deviceGroupService.getOwned(USER_ID, 7L)).thenReturn(group(7L, null));

        RuleScope normalized = service.validateAndNormalize(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_GROUP, null, List.of(5L, 5L, 7L), null), INVALID);

        assertThat(normalized.scopeType()).isEqualTo(AlertConstants.SCOPE_GROUP);
        assertThat(normalized.groupIds()).containsExactly(5L, 7L);
        assertThat(normalized.tagIds()).isEmpty();
    }

    @Test
    void validate_should_infer_all_when_type_blank_and_device_absent() {
        RuleScope normalized = service.validateAndNormalize(USER_ID,
                RuleScope.of(null, null, null, null), INVALID);

        assertThat(normalized.scopeType()).isEqualTo(AlertConstants.SCOPE_ALL);
    }

    @Test
    void validate_should_infer_device_when_type_blank_with_device() {
        RuleScope normalized = service.validateAndNormalize(USER_ID,
                RuleScope.of(null, DEVICE_ID, null, null), INVALID);

        assertThat(normalized.scopeType()).isEqualTo(AlertConstants.SCOPE_DEVICE);
        assertThat(normalized.deviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void validate_should_reject_unknown_scope_type() {
        assertThatThrownBy(() -> service.validateAndNormalize(USER_ID,
                RuleScope.of("WEIRD", null, null, null), INVALID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 6268);
    }

    @Test
    void validate_should_reject_inconsistent_fields() {
        assertThatThrownBy(() -> service.validateAndNormalize(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_ALL, DEVICE_ID, null, null), INVALID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 6268);

        assertThatThrownBy(() -> service.validateAndNormalize(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_GROUP, null, List.of(), null), INVALID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 6268);
    }

    @Test
    void validate_should_reject_group_count_over_limit() {
        scopeProperties.setMaxTargets(2);
        when(deviceGroupService.getOwned(USER_ID, 5L)).thenReturn(group(5L, null));
        when(deviceGroupService.getOwned(USER_ID, 6L)).thenReturn(group(6L, null));
        when(deviceGroupService.getOwned(USER_ID, 7L)).thenReturn(group(7L, null));

        assertThatThrownBy(() -> service.validateAndNormalize(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_GROUP, null, List.of(5L, 6L, 7L), null), INVALID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 6268);
    }

    @Test
    void validate_should_propagate_group_ownership_violation() {
        when(deviceGroupService.getOwned(USER_ID, 5L))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN));

        assertThatThrownBy(() -> service.validateAndNormalize(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_GROUP, null, List.of(5L), null), INVALID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void validate_should_not_touch_group_service_for_tag_scope() {
        when(deviceTagService.getOwned(USER_ID, 9L)).thenReturn(tag(9L));

        service.validateAndNormalize(USER_ID,
                RuleScope.of(AlertConstants.SCOPE_TAG, null, null, List.of(9L)), INVALID);

        verify(deviceGroupService, never()).getOwned(any(), any());
    }

    private static DeviceGroup group(Long id, Long parentId) {
        DeviceGroup group = new DeviceGroup();
        group.setId(id);
        group.setUserId(USER_ID);
        group.setParentId(parentId);
        return group;
    }

    private static DeviceTag tag(Long id) {
        DeviceTag tag = new DeviceTag();
        tag.setId(id);
        tag.setUserId(USER_ID);
        return tag;
    }
}