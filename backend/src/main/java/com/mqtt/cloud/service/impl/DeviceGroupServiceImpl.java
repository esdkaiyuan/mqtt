package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.GroupPermission;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.DeviceGroupMemberRequest;
import com.mqtt.cloud.dto.request.DeviceGroupRequest;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.response.DeviceGroupMemberVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.entity.DeviceGroupMember;
import com.mqtt.cloud.entity.DeviceGroupRelation;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.mapper.DeviceGroupMapper;
import com.mqtt.cloud.mapper.DeviceGroupMemberMapper;
import com.mqtt.cloud.mapper.DeviceGroupRelationMapper;
import com.mqtt.cloud.mapper.UserMapper;
import com.mqtt.cloud.service.DeviceAccessService;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备分组服务实现（T-18 设计文档 §7.1 / §8）。
 * <p>
 * 校验集中在本类：非法配置统一抛 {@code 6213}，分组不存在 {@code 6212}，越权 {@code 403}，
 * 非空分组删除 {@code 6216}；设备归属校验复用 {@link DeviceService}（不存在 {@code 2002}、
 * 非本人 {@code 2003}）。所有写入方法开启事务。
 */
@Service
public class DeviceGroupServiceImpl extends ServiceImpl<DeviceGroupMapper, DeviceGroup> implements DeviceGroupService {

    /** 分组树深度上限（根为第 1 层）。 */
    static final int MAX_DEPTH = 5;

    private final DeviceGroupRelationMapper groupRelationMapper;
    private final DeviceService deviceService;
    private final DeviceGroupMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final DeviceAccessService deviceAccessService;

    public DeviceGroupServiceImpl(DeviceGroupRelationMapper groupRelationMapper,
                                  DeviceService deviceService,
                                  DeviceGroupMemberMapper memberMapper,
                                  UserMapper userMapper,
                                  DeviceAccessService deviceAccessService) {
        this.groupRelationMapper = groupRelationMapper;
        this.deviceService = deviceService;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.deviceAccessService = deviceAccessService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceGroup create(Long userId, DeviceGroupRequest request) {
        String name = requireName(request);
        String description = requireDescription(request);
        Long parentId = request.getParentId();

        Map<Long, DeviceGroup> byId = loadById(userId);
        if (parentId != null) {
            if (!byId.containsKey(parentId)) {
                throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
            }
            if (depthOf(byId, parentId) + 1 > MAX_DEPTH) {
                throw invalid("分组层级不能超过 " + MAX_DEPTH + " 层");
            }
        }
        if (baseMapper.countByUserAndName(userId, parentId, name, null) > 0) {
            throw invalid("同级已存在同名分组");
        }

        DeviceGroup group = new DeviceGroup();
        group.setUserId(userId);
        group.setParentId(parentId);
        group.setName(name);
        group.setDescription(description);
        group.setSortOrder(resolveCreateSortOrder(userId, parentId, request.getSortOrder()));
        save(group);
        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceGroup update(Long userId, Long id, DeviceGroupRequest request) {
        DeviceGroup group = getOwned(userId, id);
        String name = requireName(request);
        String description = requireDescription(request);
        Long parentId = request.getParentId();

        Map<Long, DeviceGroup> byId = loadById(userId);
        if (parentId != null) {
            if (parentId.equals(id)) {
                throw invalid("分组不能移动到自己");
            }
            if (!byId.containsKey(parentId)) {
                throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
            }
            if (descendantIds(userId, id).contains(parentId)) {
                throw invalid("分组不能移动到自己的子分组下");
            }
            int newDepth = depthOf(byId, parentId) + 1;
            if (newDepth + subtreeHeight(byId, id) > MAX_DEPTH) {
                throw invalid("分组层级不能超过 " + MAX_DEPTH + " 层");
            }
        }
        if (baseMapper.countByUserAndName(userId, parentId, name, id) > 0) {
            throw invalid("同级已存在同名分组");
        }

        group.setName(name);
        group.setParentId(parentId);
        group.setDescription(description);
        if (request.getSortOrder() != null) {
            group.setSortOrder(request.getSortOrder());
        }
        updateById(group);
        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        getOwned(userId, id);
        if (baseMapper.countChildren(id) > 0 || groupRelationMapper.countByGroupId(id) > 0) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_EMPTY);
        }
        removeById(id);
    }

    @Override
    public List<DeviceGroup> tree(Long userId, boolean includeShared) {
        Map<Long, DeviceGroup> merged = new LinkedHashMap<>();
        for (DeviceGroup group : baseMapper.selectByUser(userId)) {
            group.setShared(false);
            group.setSharedPermission(null);
            merged.put(group.getId(), group);
        }
        if (includeShared) {
            for (DeviceGroup group : deviceAccessService.sharedGroups(userId)) {
                merged.putIfAbsent(group.getId(), group);
            }
        }
        List<DeviceGroup> groups = new ArrayList<>(merged.values());
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, DeviceGroup> byId = new LinkedHashMap<>();
        for (DeviceGroup group : groups) {
            group.setChildren(new ArrayList<>());
            group.setDeviceCount(0);
            byId.put(group.getId(), group);
        }
        List<DeviceGroup> roots = new ArrayList<>();
        for (DeviceGroup group : groups) {
            DeviceGroup parent = group.getParentId() == null ? null : byId.get(group.getParentId());
            if (parent == null) {
                roots.add(group);
            } else {
                parent.getChildren().add(group);
            }
        }
        fillDeviceCounts(byId);
        return roots;
    }

    @Override
    public List<DeviceGroup> list(Long userId, boolean includeShared) {
        Map<Long, DeviceGroup> merged = new LinkedHashMap<>();
        for (DeviceGroup group : baseMapper.selectByUser(userId)) {
            group.setShared(false);
            group.setSharedPermission(null);
            merged.put(group.getId(), group);
        }
        if (includeShared) {
            for (DeviceGroup group : deviceAccessService.sharedGroups(userId)) {
                merged.putIfAbsent(group.getId(), group);
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public DeviceGroup getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
        }
        DeviceGroup group = getById(id);
        if (group == null) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
        }
        if (!userId.equals(group.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return group;
    }

    @Override
    public DeviceGroup getAccessible(Long userId, Long id) {
        DeviceAccessService.GroupAccess access = deviceAccessService.resolveGroup(userId, id);
        if (access.group() == null) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
        }
        if (GroupPermission.NONE.equals(access.level())) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_ACCESS_DENIED);
        }
        DeviceGroup group = access.group();
        boolean owner = GroupPermission.OWNER.equals(access.level());
        group.setShared(!owner);
        group.setSharedPermission(owner ? null : access.level());
        group.setDeviceCount(groupRelationMapper.countByGroupId(group.getId()));
        return group;
    }

    @Override
    public List<DeviceGroupMemberVO> listMembers(Long userId, Long groupId) {
        getOwned(userId, groupId);
        List<DeviceGroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<DeviceGroupMember>()
                        .eq(DeviceGroupMember::getGroupId, groupId)
                        .orderByAsc(DeviceGroupMember::getId));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = members.stream().map(DeviceGroupMember::getUserId).toList();
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        return members.stream().map(member -> toMemberVO(member, users.get(member.getUserId()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceGroupMemberVO addMember(Long userId, Long groupId, DeviceGroupMemberRequest request) {
        DeviceGroup group = getOwned(userId, groupId);
        String target = request == null ? null : trimToNull(request.getUsername());
        if (target == null) {
            throw memberInvalid("请输入用户名或邮箱");
        }
        String permission = normalizePermission(request.getPermission());
        User targetUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .and(wrapper -> wrapper.eq(User::getUsername, target).or().eq(User::getEmail, target))
                .eq(User::getDeleted, 0)
                .last("LIMIT 1"));
        if (targetUser == null) {
            throw memberInvalid("目标用户不存在");
        }
        if (targetUser.getId().equals(userId) || targetUser.getId().equals(group.getUserId())) {
            throw memberInvalid("不能共享给分组所有者或自己");
        }
        if (!"ACTIVE".equals(targetUser.getStatus())) {
            throw memberInvalid("目标用户不可用");
        }
        DeviceGroupMember existing = memberMapper.selectOne(new LambdaQueryWrapper<DeviceGroupMember>()
                .eq(DeviceGroupMember::getGroupId, groupId)
                .eq(DeviceGroupMember::getUserId, targetUser.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setPermission(permission);
            memberMapper.updateById(existing);
            return toMemberVO(existing, targetUser);
        }
        DeviceGroupMember member = new DeviceGroupMember();
        member.setGroupId(groupId);
        member.setUserId(targetUser.getId());
        member.setPermission(permission);
        memberMapper.insert(member);
        return toMemberVO(member, targetUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceGroupMemberVO updateMember(Long userId, Long groupId, Long memberId,
                                            DeviceGroupMemberRequest request) {
        getOwned(userId, groupId);
        DeviceGroupMember member = getMember(groupId, memberId);
        member.setPermission(normalizePermission(request == null ? null : request.getPermission()));
        memberMapper.updateById(member);
        User user = userMapper.selectById(member.getUserId());
        return toMemberVO(member, user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long userId, Long groupId, Long memberId) {
        getOwned(userId, groupId);
        DeviceGroupMember member = getMember(groupId, memberId);
        memberMapper.deleteById(member.getId());
    }

    @Override
    public List<Long> descendantIds(Long userId, Long id) {
        List<Long> ids = baseMapper.selectDescendantIds(userId, id);
        return ids == null ? List.of() : ids;
    }

    @Override
    public List<Long> resolveFilterGroupIds(Long userId, Long groupId) {
        if (groupId == null) {
            return null;
        }
        getOwned(userId, groupId);
        return descendantIds(userId, groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDevices(Long userId, Long groupId, List<Long> deviceIds) {
        getOwned(userId, groupId);
        List<Long> ownedIds = requireOwnedDeviceIds(userId, deviceIds);
        if (ownedIds.isEmpty()) {
            return;
        }
        groupRelationMapper.insertIgnoreBatch(ownedIds, groupId, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDevices(Long userId, Long groupId, List<Long> deviceIds) {
        getOwned(userId, groupId);
        List<Long> ownedIds = requireOwnedDeviceIds(userId, deviceIds);
        if (ownedIds.isEmpty()) {
            return;
        }
        groupRelationMapper.deleteByDeviceIdsAndGroup(ownedIds, groupId);
    }

    @Override
    public IPage<Device> pageDevices(Long userId, Long groupId, long page, long size) {
        DeviceAccessService.GroupAccess access = deviceAccessService.resolveGroup(userId, groupId);
        if (access.group() == null) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
        }
        if (GroupPermission.NONE.equals(access.level())) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_ACCESS_DENIED);
        }
        DeviceGroup group = access.group();
        DeviceQueryDTO dto = new DeviceQueryDTO();
        // 所有者按 ownerId 过滤；共享成员只看该分组子树（分组本身已限定归属）
        dto.setOwnerId(GroupPermission.OWNER.equals(access.level()) ? group.getUserId() : null);
        dto.setGroupIds(baseMapper.selectDescendantIds(group.getUserId(), groupId));
        dto.setPageNum((int) page);
        dto.setPageSize((int) size);
        IPage<Device> result = deviceService.getDevicesInGroups(dto);
        for (Device device : result.getRecords()) {
            device.setAccessLevel(access.level());
        }
        return result;
    }

    @Override
    public Map<Long, List<DeviceGroup>> groupsByDeviceIds(Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }
        List<DeviceGroupRelation> relations = groupRelationMapper.selectByDeviceIds(deviceIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> groupIds = relations.stream().map(DeviceGroupRelation::getGroupId).collect(Collectors.toSet());
        Map<Long, DeviceGroup> byId = baseMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(DeviceGroup::getId, g -> g, (a, b) -> a));
        Map<Long, List<DeviceGroup>> result = new HashMap<>();
        for (DeviceGroupRelation relation : relations) {
            DeviceGroup group = byId.get(relation.getGroupId());
            if (group != null) {
                result.computeIfAbsent(relation.getDeviceId(), k -> new ArrayList<>()).add(group);
            }
        }
        return result;
    }

    @Override
    public List<Long> deviceIdsByGroupIds(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        return groupRelationMapper.selectDeviceIdsByGroupIds(groupIds);
    }

    private DeviceGroupMember getMember(Long groupId, Long memberId) {
        if (memberId == null) {
            throw memberNotFound();
        }
        DeviceGroupMember member = memberMapper.selectById(memberId);
        if (member == null || !groupId.equals(member.getGroupId())) {
            throw memberNotFound();
        }
        return member;
    }

    private DeviceGroupMemberVO toMemberVO(DeviceGroupMember member, User user) {
        DeviceGroupMemberVO vo = new DeviceGroupMemberVO();
        vo.setId(member.getId());
        vo.setUserId(member.getUserId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setEmail(user == null ? null : user.getEmail());
        vo.setPermission(member.getPermission());
        vo.setCreatedAt(member.getCreatedAt());
        return vo;
    }

    private static String normalizePermission(String permission) {
        String value = permission == null ? null : permission.trim().toUpperCase();
        if (!GroupPermission.SHARED.contains(value)) {
            throw memberInvalid("权限必须是 VIEW / OPERATE");
        }
        return value;
    }

    private static BusinessException memberInvalid(String message) {
        return new BusinessException(ResultCode.DEVICE_GROUP_MEMBER_INVALID, message);
    }

    private static BusinessException memberNotFound() {
        return new BusinessException(ResultCode.DEVICE_GROUP_MEMBER_NOT_FOUND);
    }

    private String requireName(DeviceGroupRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空");
        }
        String name = trimToNull(request.getName());
        if (name == null) {
            throw invalid("分组名称不能为空");
        }
        if (name.length() > 64) {
            throw invalid("分组名称长度不能超过 64");
        }
        return name;
    }

    private String requireDescription(DeviceGroupRequest request) {
        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > 255) {
            throw invalid("分组描述长度不能超过 255");
        }
        return description;
    }

    /** 创建时的排序值：显式传入优先，否则追加到同级末尾。 */
    private int resolveCreateSortOrder(Long userId, Long parentId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        Integer max = baseMapper.selectMaxSortOrder(userId, parentId);
        return max == null ? 0 : max + 1;
    }

    /** 加载本人全部分组为 id → 节点映射，供深度与子树高度计算（分组量级为百，内存计算成本可忽略）。 */
    private Map<Long, DeviceGroup> loadById(Long userId) {
        return baseMapper.selectByUser(userId).stream()
                .collect(Collectors.toMap(DeviceGroup::getId, g -> g, (a, b) -> a, LinkedHashMap::new));
    }

    /** 节点深度（根为 1）；带 visited 守卫，避免脏数据成环时死循环。 */
    private int depthOf(Map<Long, DeviceGroup> byId, Long id) {
        int depth = 0;
        Long cursor = id;
        Set<Long> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            depth++;
            DeviceGroup node = byId.get(cursor);
            cursor = node == null ? null : node.getParentId();
        }
        return depth;
    }

    /** 以某节点为根的子树高度（自身为 0，每下一层 +1）。 */
    private int subtreeHeight(Map<Long, DeviceGroup> byId, Long rootId) {
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (DeviceGroup group : byId.values()) {
            if (group.getParentId() != null) {
                childrenByParent.computeIfAbsent(group.getParentId(), k -> new ArrayList<>()).add(group.getId());
            }
        }
        return heightOf(childrenByParent, rootId);
    }

    private int heightOf(Map<Long, List<Long>> childrenByParent, Long node) {
        List<Long> children = childrenByParent.get(node);
        if (children == null || children.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (Long child : children) {
            max = Math.max(max, 1 + heightOf(childrenByParent, child));
        }
        return max;
    }

    /** 一次性批量装配各分组的直接关联设备数，避免逐节点查询。 */
    private void fillDeviceCounts(Map<Long, DeviceGroup> byId) {
        List<Map<String, Object>> rows = groupRelationMapper.countByGroupIds(new ArrayList<>(byId.keySet()));
        for (Map<String, Object> row : rows) {
            Object groupId = row.get("groupId");
            Object count = row.get("cnt");
            if (groupId instanceof Number gid && count instanceof Number cnt) {
                DeviceGroup group = byId.get(gid.longValue());
                if (group != null) {
                    group.setDeviceCount(cnt.intValue());
                }
            }
        }
    }

    /** 校验设备归属：不存在 / 已删除 {@code 2002}，非本人 {@code 2003}；返回去重后的本人设备 ID。 */
    private List<Long> requireOwnedDeviceIds(Long userId, List<Long> deviceIds) {
        List<Long> distinct = normalize(deviceIds);
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Device> devices = deviceService.listByIds(distinct);
        if (devices.size() != distinct.size()) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        for (Device device : devices) {
            if (!userId.equals(device.getOwnerId())) {
                throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
            }
        }
        return devices.stream().map(Device::getId).toList();
    }

    private List<Long> normalize(List<Long> deviceIds) {
        if (deviceIds == null) {
            return List.of();
        }
        return deviceIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResultCode.DEVICE_GROUP_INVALID, message);
    }
}