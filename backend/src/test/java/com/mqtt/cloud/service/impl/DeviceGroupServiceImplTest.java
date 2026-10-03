package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.DeviceGroupRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.mapper.DeviceGroupMapper;
import com.mqtt.cloud.mapper.DeviceGroupRelationMapper;
import com.mqtt.cloud.service.DeviceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备分组服务单测（T-18 实施计划 P5）。
 * <p>
 * 覆盖创建（同级重名 / 非法父分组）、更新（成环 / 超深 / 移动到根）、删除（非空 / 空成功）、
 * 后代查询与归属越权（{@code 403}）。
 */
@ExtendWith(MockitoExtension.class)
class DeviceGroupServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private DeviceGroupMapper groupMapper;

    @Mock
    private DeviceGroupRelationMapper groupRelationMapper;

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private DeviceGroupServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper / 实体 TableInfo 需要初始化；纯单测无 MyBatis 上下文
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DeviceGroup.class);
        // 显式构造器（DeviceGroupRelationMapper / DeviceService），Mockito 不做字段注入，需手动装配 baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", groupMapper);
    }

    @Test
    void create_should_save_root_group_with_appended_sort_order() {
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of());
        when(groupMapper.countByUserAndName(anyLong(), any(), anyString(), any())).thenReturn(0);
        when(groupMapper.selectMaxSortOrder(anyLong(), any())).thenReturn(null);

        DeviceGroup created = service.create(USER_ID, request("华东厂区", null));

        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getName()).isEqualTo("华东厂区");
        assertThat(created.getSortOrder()).isZero();
        verify(groupMapper).insert(any(DeviceGroup.class));
    }

    @Test
    void create_should_append_after_existing_siblings() {
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of());
        when(groupMapper.countByUserAndName(anyLong(), any(), anyString(), any())).thenReturn(0);
        when(groupMapper.selectMaxSortOrder(anyLong(), any())).thenReturn(3);

        DeviceGroup created = service.create(USER_ID, request("2 号车间", null));

        assertThat(created.getSortOrder()).isEqualTo(4);
    }

    @Test
    void create_should_reject_duplicate_sibling_name() {
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of());
        when(groupMapper.countByUserAndName(anyLong(), any(), anyString(), any())).thenReturn(1);

        assertCode(() -> service.create(USER_ID, request("华东厂区", null)), ResultCode.DEVICE_GROUP_INVALID);
    }

    @Test
    void create_should_reject_unknown_parent() {
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of());

        assertCode(() -> service.create(USER_ID, request("1 号车间", 99L)), ResultCode.DEVICE_GROUP_NOT_FOUND);
    }

    @Test
    void create_should_reject_blank_name() {
        assertCode(() -> service.create(USER_ID, request("   ", null)), ResultCode.DEVICE_GROUP_INVALID);
    }

    @Test
    void update_should_reject_moving_group_into_its_descendant() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of(group(1L, null), group(2L, 1L)));
        when(groupMapper.selectDescendantIds(USER_ID, 1L)).thenReturn(List.of(1L, 2L));

        assertCode(() -> service.update(USER_ID, 1L, request("华东厂区", 2L)), ResultCode.DEVICE_GROUP_INVALID);
        verify(groupMapper, never()).updateById(any(DeviceGroup.class));
    }

    @Test
    void update_should_reject_exceeding_max_depth() {
        List<DeviceGroup> all = List.of(
                group(1L, null), group(2L, 1L), group(3L, 2L), group(4L, 3L), group(5L, 4L), group(99L, null));
        when(groupMapper.selectById(99L)).thenReturn(group(99L, null));
        when(groupMapper.selectByUser(USER_ID)).thenReturn(all);
        when(groupMapper.selectDescendantIds(USER_ID, 99L)).thenReturn(List.of(99L));

        assertCode(() -> service.update(USER_ID, 99L, request("深节点", 5L)), ResultCode.DEVICE_GROUP_INVALID);
    }

    @Test
    void update_should_rename_and_move_to_root() {
        DeviceGroup group = group(2L, 1L);
        when(groupMapper.selectById(2L)).thenReturn(group);
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of(group(1L, null), group));
        when(groupMapper.countByUserAndName(anyLong(), any(), anyString(), any())).thenReturn(0);

        DeviceGroup updated = service.update(USER_ID, 2L, request("1 号车间", null));

        assertThat(updated.getParentId()).isNull();
        assertThat(updated.getName()).isEqualTo("1 号车间");
        verify(groupMapper).updateById(group);
    }

    @Test
    void delete_should_reject_group_with_children() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(groupMapper.countChildren(1L)).thenReturn(1);

        assertCode(() -> service.delete(USER_ID, 1L), ResultCode.DEVICE_GROUP_NOT_EMPTY);
        verify(groupMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_should_reject_group_with_devices() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(groupMapper.countChildren(1L)).thenReturn(0);
        when(groupRelationMapper.countByGroupId(1L)).thenReturn(2);

        assertCode(() -> service.delete(USER_ID, 1L), ResultCode.DEVICE_GROUP_NOT_EMPTY);
    }

    @Test
    void delete_should_remove_empty_group() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(groupMapper.countChildren(1L)).thenReturn(0);
        when(groupRelationMapper.countByGroupId(1L)).thenReturn(0);

        service.delete(USER_ID, 1L);

        verify(groupMapper).deleteById(1L);
    }

    @Test
    void tree_should_assemble_children_and_device_counts() {
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of(group(1L, null), group(2L, 1L)));
        when(groupRelationMapper.countByGroupIds(any())).thenReturn(List.of(
                Map.<String, Object>of("groupId", 1L, "cnt", 2L),
                Map.<String, Object>of("groupId", 2L, "cnt", 1L)));

        List<DeviceGroup> roots = service.tree(USER_ID);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getDeviceCount()).isEqualTo(2);
        assertThat(roots.get(0).getChildren()).extracting(DeviceGroup::getId).containsExactly(2L);
        assertThat(roots.get(0).getChildren().get(0).getDeviceCount()).isEqualTo(1);
    }

    @Test
    void tree_should_return_empty_when_no_groups() {
        when(groupMapper.selectByUser(USER_ID)).thenReturn(List.of());

        assertThat(service.tree(USER_ID)).isEmpty();
    }

    @Test
    void descendantIds_should_include_self_and_descendants() {
        when(groupMapper.selectDescendantIds(USER_ID, 1L)).thenReturn(List.of(1L, 2L, 3L));

        assertThat(service.descendantIds(USER_ID, 1L)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void resolveFilterGroupIds_should_return_null_when_no_filter() {
        assertThat(service.resolveFilterGroupIds(USER_ID, null)).isNull();
    }

    @Test
    void resolveFilterGroupIds_should_validate_and_expand() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(groupMapper.selectDescendantIds(USER_ID, 1L)).thenReturn(List.of(1L, 2L));

        assertThat(service.resolveFilterGroupIds(USER_ID, 1L)).containsExactly(1L, 2L);
    }

    @Test
    void getOwned_should_throw_when_missing() {
        when(groupMapper.selectById(5L)).thenReturn(null);

        assertCode(() -> service.getOwned(USER_ID, 5L), ResultCode.DEVICE_GROUP_NOT_FOUND);
    }

    @Test
    void getOwned_should_throw_when_not_owned() {
        DeviceGroup group = group(5L, null);
        group.setUserId(999L);
        when(groupMapper.selectById(5L)).thenReturn(group);

        assertCode(() -> service.getOwned(USER_ID, 5L), ResultCode.FORBIDDEN);
    }

    @Test
    void addDevices_should_insert_ignore_after_ownership_check() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(100L, USER_ID), device(101L, USER_ID)));

        service.addDevices(USER_ID, 1L, List.of(100L, 101L));

        verify(groupRelationMapper).insertIgnoreBatch(any(), eq(1L), any());
    }

    @Test
    void addDevices_should_reject_non_owned_device() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(100L, 999L)));

        assertCode(() -> service.addDevices(USER_ID, 1L, List.of(100L)), ResultCode.DEVICE_NOT_OWNED);
    }

    @Test
    void addDevices_should_reject_missing_device() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));
        when(deviceService.listByIds(any())).thenReturn(List.of());

        assertCode(() -> service.addDevices(USER_ID, 1L, List.of(100L)), ResultCode.DEVICE_NOT_FOUND);
    }

    @Test
    void addDevices_should_noop_on_empty_input() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, null));

        service.addDevices(USER_ID, 1L, List.of());

        verify(groupRelationMapper, never()).insertIgnoreBatch(any(), anyLong(), any());
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private DeviceGroupRequest request(String name, Long parentId) {
        DeviceGroupRequest request = new DeviceGroupRequest();
        request.setName(name);
        request.setParentId(parentId);
        return request;
    }

    private DeviceGroup group(Long id, Long parentId) {
        DeviceGroup group = new DeviceGroup();
        group.setId(id);
        group.setParentId(parentId);
        group.setUserId(USER_ID);
        group.setName("g-" + id);
        return group;
    }

    private Device device(Long id, Long ownerId) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerId(ownerId);
        return device;
    }
}