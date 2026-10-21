package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mqtt.cloud.common.constant.GroupPermission;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.entity.DeviceGroupMember;
import com.mqtt.cloud.entity.DeviceGroupRelation;
import com.mqtt.cloud.mapper.DeviceGroupMapper;
import com.mqtt.cloud.mapper.DeviceGroupMemberMapper;
import com.mqtt.cloud.mapper.DeviceGroupRelationMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分组级权限与设备访问判定实现（T-48）。
 * <p>
 * 读取路径不做缓存：成员变更 / 分组移动后立即生效，避免多副本缓存失效问题。
 * 分组最大 5 层，祖先链逐级查询成本可接受。
 */
@Service
@RequiredArgsConstructor
public class DeviceAccessServiceImpl implements DeviceAccessService {

    private final DeviceGroupMapper groupMapper;
    private final DeviceGroupMemberMapper memberMapper;
    private final DeviceGroupRelationMapper relationMapper;
    private final DeviceMapper deviceMapper;

    @Override
    public String resolveGroupLevel(Long userId, Long groupId) {
        return resolveGroup(userId, groupId).level();
    }

    @Override
    public GroupAccess resolveGroup(Long userId, Long groupId) {
        DeviceGroup group = loadGroup(groupId);
        if (group == null) {
            return new GroupAccess(null, GroupPermission.NONE);
        }
        if (userId != null && userId.equals(group.getUserId())) {
            return new GroupAccess(group, GroupPermission.OWNER);
        }
        DeviceGroup current = group;
        Set<Long> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current.getId())) {
            DeviceGroupMember member = memberMapper.selectOne(new LambdaQueryWrapper<DeviceGroupMember>()
                    .eq(DeviceGroupMember::getGroupId, current.getId())
                    .eq(DeviceGroupMember::getUserId, userId)
                    .last("LIMIT 1"));
            if (member != null) {
                return new GroupAccess(group, normalizeLevel(member.getPermission()));
            }
            current = current.getParentId() == null ? null : loadGroup(current.getParentId());
        }
        return new GroupAccess(group, GroupPermission.NONE);
    }

    @Override
    public String resolveDeviceLevel(Long userId, String role, Long deviceId) {
        Device device = deviceId == null ? null : deviceMapper.selectById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            return GroupPermission.NONE;
        }
        if (userId != null && userId.equals(device.getOwnerId())) {
            return GroupPermission.OWNER;
        }
        if ("ADMIN".equals(role)) {
            return GroupPermission.OWNER;
        }
        List<DeviceGroupRelation> relations = relationMapper.selectByDeviceIds(List.of(deviceId));
        String level = GroupPermission.NONE;
        for (DeviceGroupRelation relation : relations) {
            String groupLevel = resolveGroupLevel(userId, relation.getGroupId());
            level = GroupPermission.max(level, groupLevel);
            if (GroupPermission.OPERATE.equals(level)) {
                return level;
            }
        }
        return level;
    }

    @Override
    public boolean canViewDevice(Long userId, String role, Long deviceId) {
        return GroupPermission.canView(resolveDeviceLevel(userId, role, deviceId));
    }

    @Override
    public boolean canOperateDevice(Long userId, String role, Long deviceId) {
        return GroupPermission.canOperate(resolveDeviceLevel(userId, role, deviceId));
    }

    @Override
    public List<DeviceGroup> sharedGroups(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<DeviceGroupMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<DeviceGroupMember>()
                        .eq(DeviceGroupMember::getUserId, userId)
                        .orderByAsc(DeviceGroupMember::getId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, String> permissions = new LinkedHashMap<>();
        Set<Long> groupIds = new LinkedHashSet<>();
        for (DeviceGroupMember membership : memberships) {
            DeviceGroup root = loadGroup(membership.getGroupId());
            if (root == null || userId.equals(root.getUserId())) {
                continue;
            }
            List<Long> subtree = groupMapper.selectDescendantIds(root.getUserId(), root.getId());
            if (subtree == null) {
                continue;
            }
            for (Long id : subtree) {
                groupIds.add(id);
                permissions.merge(id, normalizeLevel(membership.getPermission()), GroupPermission::max);
            }
        }
        if (groupIds.isEmpty()) {
            return List.of();
        }
        List<DeviceGroup> groups = groupMapper.selectBatchIds(groupIds);
        List<DeviceGroup> result = new ArrayList<>(groups.size());
        for (DeviceGroup group : groups) {
            if (group == null || userId.equals(group.getUserId())) {
                continue;
            }
            group.setShared(true);
            group.setSharedPermission(permissions.getOrDefault(group.getId(), GroupPermission.VIEW));
            result.add(group);
        }
        return result;
    }

    private DeviceGroup loadGroup(Long id) {
        if (id == null) {
            return null;
        }
        DeviceGroup group = groupMapper.selectById(id);
        return group == null || Boolean.TRUE.equals(group.getDeleted()) ? null : group;
    }

    private static String normalizeLevel(String permission) {
        String value = permission == null ? null : permission.trim().toUpperCase();
        return GroupPermission.SHARED.contains(value) ? value : GroupPermission.VIEW;
    }
}
