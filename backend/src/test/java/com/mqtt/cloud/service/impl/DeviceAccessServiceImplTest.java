package com.mqtt.cloud.service.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAccessServiceImplTest {

    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long GROUP_ID = 1L;
    private static final Long CHILD_GROUP_ID = 2L;
    private static final Long DEVICE_ID = 100L;

    @Mock
    private DeviceGroupMapper groupMapper;
    @Mock
    private DeviceGroupMemberMapper memberMapper;
    @Mock
    private DeviceGroupRelationMapper relationMapper;
    @Mock
    private DeviceMapper deviceMapper;

    private DeviceAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceAccessServiceImpl(groupMapper, memberMapper, relationMapper, deviceMapper);
    }

    @Test
    void resolveGroup_should_return_owner_level_for_group_owner() {
        when(groupMapper.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, OWNER_ID, null));

        DeviceAccessService.GroupAccess access = service.resolveGroup(OWNER_ID, GROUP_ID);

        assertThat(access.level()).isEqualTo(GroupPermission.OWNER);
        assertThat(access.group().getId()).isEqualTo(GROUP_ID);
    }

    @Test
    void resolveGroup_should_inherit_membership_from_ancestor() {
        DeviceGroup child = group(CHILD_GROUP_ID, OWNER_ID, GROUP_ID);
        DeviceGroup parent = group(GROUP_ID, OWNER_ID, null);
        when(groupMapper.selectById(CHILD_GROUP_ID)).thenReturn(child);
        when(groupMapper.selectById(GROUP_ID)).thenReturn(parent);
        DeviceGroupMember member = member(MEMBER_ID, GROUP_ID, GroupPermission.VIEW);
        // 先查子分组（无成员），再查父分组（命中）
        when(memberMapper.selectOne(any())).thenReturn(null, member);

        DeviceAccessService.GroupAccess access = service.resolveGroup(MEMBER_ID, CHILD_GROUP_ID);

        assertThat(access.level()).isEqualTo(GroupPermission.VIEW);
        assertThat(access.group().getId()).isEqualTo(CHILD_GROUP_ID);
    }

    @Test
    void resolveGroup_should_reject_unrelated_user() {
        when(groupMapper.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, OWNER_ID, null));
        when(memberMapper.selectOne(any())).thenReturn(null);

        assertThat(service.resolveGroupLevel(MEMBER_ID, GROUP_ID)).isEqualTo(GroupPermission.NONE);
    }

    @Test
    void resolveDeviceLevel_should_take_highest_permission_across_groups() {
        DeviceAccessServiceImpl spied = spy(service);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, OWNER_ID));
        when(relationMapper.selectByDeviceIds(anyCollection())).thenReturn(List.of(
                relation(DEVICE_ID, GROUP_ID), relation(DEVICE_ID, CHILD_GROUP_ID)));
        doReturn(GroupPermission.VIEW).when(spied).resolveGroupLevel(MEMBER_ID, GROUP_ID);
        doReturn(GroupPermission.OPERATE).when(spied).resolveGroupLevel(MEMBER_ID, CHILD_GROUP_ID);

        assertThat(spied.resolveDeviceLevel(MEMBER_ID, "VIEWER", DEVICE_ID))
                .isEqualTo(GroupPermission.OPERATE);
    }

    @Test
    void resolveDeviceLevel_should_allow_owner_and_admin_and_reject_unknown_device() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, OWNER_ID));
        when(deviceMapper.selectById(404L)).thenReturn(null);

        assertThat(service.resolveDeviceLevel(OWNER_ID, "VIEWER", DEVICE_ID))
                .isEqualTo(GroupPermission.OWNER);
        assertThat(service.resolveDeviceLevel(MEMBER_ID, "ADMIN", DEVICE_ID))
                .isEqualTo(GroupPermission.OWNER);
        assertThat(service.resolveDeviceLevel(MEMBER_ID, "VIEWER", 404L))
                .isEqualTo(GroupPermission.NONE);
    }

    @Test
    void sharedGroups_should_expand_subtree_and_mark_permission() {
        DeviceGroup root = group(GROUP_ID, OWNER_ID, null);
        DeviceGroup child = group(CHILD_GROUP_ID, OWNER_ID, GROUP_ID);
        when(memberMapper.selectList(any())).thenReturn(List.of(member(MEMBER_ID, GROUP_ID, GroupPermission.OPERATE)));
        when(groupMapper.selectById(GROUP_ID)).thenReturn(root);
        when(groupMapper.selectDescendantIds(OWNER_ID, GROUP_ID)).thenReturn(List.of(GROUP_ID, CHILD_GROUP_ID));
        when(groupMapper.selectBatchIds(anyCollection())).thenReturn(List.of(root, child));

        List<DeviceGroup> groups = service.sharedGroups(MEMBER_ID);

        assertThat(groups).hasSize(2);
        assertThat(groups).allSatisfy(group -> {
            assertThat(group.getShared()).isTrue();
            assertThat(group.getSharedPermission()).isEqualTo(GroupPermission.OPERATE);
        });
    }

    private DeviceGroup group(Long id, Long userId, Long parentId) {
        DeviceGroup group = new DeviceGroup();
        group.setId(id);
        group.setUserId(userId);
        group.setParentId(parentId);
        return group;
    }

    private DeviceGroupMember member(Long userId, Long groupId, String permission) {
        DeviceGroupMember member = new DeviceGroupMember();
        member.setUserId(userId);
        member.setGroupId(groupId);
        member.setPermission(permission);
        return member;
    }

    private Device device(Long id, Long ownerId) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerId(ownerId);
        device.setDeleted(0);
        return device;
    }

    private DeviceGroupRelation relation(Long deviceId, Long groupId) {
        DeviceGroupRelation relation = new DeviceGroupRelation();
        relation.setDeviceId(deviceId);
        relation.setGroupId(groupId);
        return relation;
    }
}
