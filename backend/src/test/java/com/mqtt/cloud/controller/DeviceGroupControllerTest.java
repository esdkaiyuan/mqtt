package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.request.DeviceGroupRequest;
import com.mqtt.cloud.dto.request.DeviceIdsRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceGroupTagAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备分组控制台接口单测（T-18 实施计划 P5）。
 * <p>
 * 聚焦「当前用户透传 + 参数下传 + 结果封装 + 分组内设备批量装配」。
 */
@ExtendWith(MockitoExtension.class)
class DeviceGroupControllerTest {

    private static final Long USER_ID = 10L;

    @Mock
    private DeviceGroupService deviceGroupService;

    @Mock
    private DeviceGroupTagAssembler assembler;

    @InjectMocks
    private DeviceGroupController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tree_should_delegate_for_current_user() {
        authenticate();
        DeviceGroup root = new DeviceGroup();
        when(deviceGroupService.tree(USER_ID)).thenReturn(List.of(root));

        Result<List<DeviceGroup>> result = controller.tree();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(root);
    }

    @Test
    void list_should_delegate_for_current_user() {
        authenticate();
        DeviceGroup group = new DeviceGroup();
        when(deviceGroupService.list(USER_ID)).thenReturn(List.of(group));

        assertThat(controller.list().getData()).containsExactly(group);
    }

    @Test
    void create_should_delegate_with_current_user() {
        authenticate();
        DeviceGroupRequest request = new DeviceGroupRequest();
        DeviceGroup created = new DeviceGroup();
        when(deviceGroupService.create(USER_ID, request)).thenReturn(created);

        assertThat(controller.create(request).getData()).isSameAs(created);
    }

    @Test
    void get_should_delegate() {
        authenticate();
        DeviceGroup group = new DeviceGroup();
        when(deviceGroupService.getOwned(USER_ID, 3L)).thenReturn(group);

        assertThat(controller.get(3L).getData()).isSameAs(group);
    }

    @Test
    void update_should_delegate() {
        authenticate();
        DeviceGroupRequest request = new DeviceGroupRequest();
        DeviceGroup updated = new DeviceGroup();
        when(deviceGroupService.update(USER_ID, 3L, request)).thenReturn(updated);

        assertThat(controller.update(3L, request).getData()).isSameAs(updated);
    }

    @Test
    void delete_should_delegate() {
        authenticate();

        controller.delete(3L);

        verify(deviceGroupService).delete(USER_ID, 3L);
    }

    @Test
    void pageDevices_should_delegate_and_assemble() {
        authenticate();
        Device device = new Device();
        Page<Device> page = new Page<>(1, 10);
        page.setRecords(List.of(device));
        when(deviceGroupService.pageDevices(USER_ID, 3L, 1, 10)).thenReturn(page);

        IPage<Device> result = controller.pageDevices(3L, 1, 10).getData();

        assertThat(result.getRecords()).containsExactly(device);
        verify(assembler).assemble(page.getRecords());
    }

    @Test
    void addDevices_should_delegate_device_ids() {
        authenticate();
        DeviceIdsRequest request = new DeviceIdsRequest();
        request.setDeviceIds(List.of(100L, 101L));

        controller.addDevices(3L, request);

        verify(deviceGroupService).addDevices(USER_ID, 3L, List.of(100L, 101L));
    }

    @Test
    void removeDevices_should_delegate_device_ids() {
        authenticate();
        DeviceIdsRequest request = new DeviceIdsRequest();
        request.setDeviceIds(List.of(100L));

        controller.removeDevices(3L, request);

        verify(deviceGroupService).removeDevices(USER_ID, 3L, List.of(100L));
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}