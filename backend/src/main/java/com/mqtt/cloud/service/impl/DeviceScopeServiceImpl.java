package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.ScopeProperties;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.entity.DeviceTag;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceScopeService;
import com.mqtt.cloud.service.DeviceTagService;
import com.mqtt.cloud.service.scope.DeviceMemberships;
import com.mqtt.cloud.service.scope.RuleScope;
import com.mqtt.cloud.util.ScopeJson;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备作用域解析服务实现（T-28 设计文档 §4.3 / §4.4 / §6）。
 * <p>
 * 只读复用分组 / 标签服务的查询与归属校验，不写分组与标签域；分组档匹配采用「设备隶属上溯祖先」
 * 而非「落库预展开」，使作用域与分组树变更解耦（设计文档 §4.3）。
 */
@Service
public class DeviceScopeServiceImpl implements DeviceScopeService {

    private final DeviceGroupService deviceGroupService;
    private final DeviceTagService deviceTagService;
    private final ScopeProperties scopeProperties;
    private final ScopeJson scopeJson;

    public DeviceScopeServiceImpl(DeviceGroupService deviceGroupService,
                                  DeviceTagService deviceTagService,
                                  ScopeProperties scopeProperties,
                                  ScopeJson scopeJson) {
        this.deviceGroupService = deviceGroupService;
        this.deviceTagService = deviceTagService;
        this.scopeProperties = scopeProperties;
        this.scopeJson = scopeJson;
    }

    @Override
    public DeviceMemberships memberships(Long userId, Long deviceId) {
        if (deviceId == null) {
            return DeviceMemberships.empty();
        }
        List<Long> directGroupIds = deviceGroupService.groupsByDeviceIds(List.of(deviceId))
                .getOrDefault(deviceId, List.of()).stream()
                .map(DeviceGroup::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<Long> groupIds = withAncestors(userId, directGroupIds);
        Set<Long> tagIds = deviceTagService.tagsByDeviceIds(List.of(deviceId))
                .getOrDefault(deviceId, List.of()).stream()
                .map(DeviceTag::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new DeviceMemberships(groupIds, tagIds);
    }

    @Override
    public List<Long> deviceIds(Long userId, RuleScope scope) {
        String scopeType = scope == null ? null : scope.scopeType();
        if (scopeType == null) {
            return List.of();
        }
        return switch (scopeType) {
            case AlertConstants.SCOPE_ALL -> List.of();
            case AlertConstants.SCOPE_DEVICE ->
                    scope.deviceId() == null ? List.of() : List.of(scope.deviceId());
            case AlertConstants.SCOPE_GROUP -> groupDeviceIds(userId, scope.groupIds());
            case AlertConstants.SCOPE_TAG -> tagDeviceIds(scope.tagIds());
            default -> List.of();
        };
    }

    @Override
    public RuleScope toScope(String scopeType, Long deviceId, String groupIdsJson, String tagIdsJson) {
        return RuleScope.of(inferType(scopeType, deviceId), deviceId,
                scopeJson.toIdList(groupIdsJson), scopeJson.toIdList(tagIdsJson));
    }

    @Override
    public RuleScope validateAndNormalize(Long userId, RuleScope scope, ResultCode invalidCode) {
        if (scope == null) {
            throw new BusinessException(invalidCode, "作用域不能为空");
        }
        String scopeType = resolveType(scope.scopeType(), scope.deviceId(), invalidCode);
        List<Long> groupIds = distinct(scope.groupIds());
        List<Long> tagIds = distinct(scope.tagIds());

        if (AlertConstants.SCOPE_ALL.equals(scopeType)) {
            if (scope.deviceId() != null || !groupIds.isEmpty() || !tagIds.isEmpty()) {
                throw new BusinessException(invalidCode, "全部设备档不允许指定设备 / 分组 / 标签");
            }
            return RuleScope.of(scopeType, null, List.of(), List.of());
        }
        if (AlertConstants.SCOPE_DEVICE.equals(scopeType)) {
            if (scope.deviceId() == null || !groupIds.isEmpty() || !tagIds.isEmpty()) {
                throw new BusinessException(invalidCode, "指定设备档要求且仅要求 deviceId");
            }
            return RuleScope.of(scopeType, scope.deviceId(), List.of(), List.of());
        }
        if (AlertConstants.SCOPE_GROUP.equals(scopeType)) {
            if (scope.deviceId() != null || groupIds.isEmpty() || !tagIds.isEmpty()) {
                throw new BusinessException(invalidCode, "分组档要求且仅要求 groupIds");
            }
            groupIds.forEach(groupId -> deviceGroupService.getOwned(userId, groupId));
            requireWithinLimit(groupIds.size(), invalidCode);
            return RuleScope.of(scopeType, null, groupIds, List.of());
        }
        if (AlertConstants.SCOPE_TAG.equals(scopeType)) {
            if (scope.deviceId() != null || !groupIds.isEmpty() || tagIds.isEmpty()) {
                throw new BusinessException(invalidCode, "标签档要求且仅要求 tagIds");
            }
            tagIds.forEach(tagId -> deviceTagService.getOwned(userId, tagId));
            requireWithinLimit(tagIds.size(), invalidCode);
            return RuleScope.of(scopeType, null, List.of(), tagIds);
        }
        throw new BusinessException(invalidCode, "作用域档位非法：" + scopeType);
    }

    /** 作用域设备集合的 GROUP 档：选中分组展开为「自身 + 后代」后取关联设备。 */
    private List<Long> groupDeviceIds(Long userId, Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        Set<Long> expanded = new LinkedHashSet<>();
        for (Long groupId : groupIds) {
            expanded.addAll(deviceGroupService.descendantIds(userId, groupId));
        }
        return distinct(deviceGroupService.deviceIdsByGroupIds(expanded));
    }

    /** 作用域设备集合的 TAG 档：选中标签关联设备。 */
    private List<Long> tagDeviceIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return distinct(deviceTagService.deviceIdsByTagIds(tagIds));
    }

    /** 直接分组 ∪ 沿 {@code parent_id} 上溯的全部祖先；带 visited 守卫，避免脏数据成环时死循环。 */
    private Set<Long> withAncestors(Long userId, Collection<Long> directGroupIds) {
        if (directGroupIds.isEmpty()) {
            return Set.of();
        }
        Map<Long, Long> parentById = new HashMap<>();
        for (DeviceGroup group : deviceGroupService.list(userId)) {
            if (group.getId() != null) {
                parentById.put(group.getId(), group.getParentId());
            }
        }
        Set<Long> result = new LinkedHashSet<>(directGroupIds);
        for (Long groupId : directGroupIds) {
            Set<Long> seen = new HashSet<>();
            seen.add(groupId);
            Long cursor = parentById.get(groupId);
            while (cursor != null && seen.add(cursor)) {
                result.add(cursor);
                cursor = parentById.get(cursor);
            }
        }
        return result;
    }

    private String resolveType(String scopeType, Long deviceId, ResultCode invalidCode) {
        String type = inferType(scopeType, deviceId);
        if (!AlertConstants.SCOPES.contains(type)) {
            throw new BusinessException(invalidCode, "作用域档位非法：" + scopeType);
        }
        return type;
    }

    /** 档位为空时按 {@code deviceId} 是否为空推断为 {@code DEVICE} / {@code ALL}；非空档位仅去空白，不做合法性校验。 */
    private String inferType(String scopeType, Long deviceId) {
        if (scopeType == null || scopeType.isBlank()) {
            return deviceId != null ? AlertConstants.SCOPE_DEVICE : AlertConstants.SCOPE_ALL;
        }
        return scopeType.trim();
    }

    private void requireWithinLimit(int size, ResultCode invalidCode) {
        int max = scopeProperties.getMaxTargets();
        if (max > 0 && size > max) {
            throw new BusinessException(invalidCode, "作用域内分组 / 标签数量不能超过 " + max);
        }
    }

    private List<Long> distinct(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}