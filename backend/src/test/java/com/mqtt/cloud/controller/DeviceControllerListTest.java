package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceGroupTagAssembler;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceShadowService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import com.mqtt.cloud.service.DeviceTagService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备列表分组 / 标签筛选与装配单测（T-18 实施计划 P5）。
 * <p>
 * 聚焦 §10.4：客户端只传 {@code groupId} / {@code tagId}，服务端校验归属并解析为内部集合；
 * 分页结果批量装配 {@code groups} / {@code tags}。
 */
@ExtendWith(MockitoExtension.class)
class DeviceControllerListTest {

    private static final Long USER_ID = 10L;

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceStatusHistoryService deviceStatusHistoryService;

    @Mock
    private DeviceCredentialService deviceCredentialService;

    @Mock
    private DeviceDataService deviceDataService;

    @Mock
    private DeviceCommandService deviceCommandService;

    @Mock
    private DeviceShadowService deviceShadowService;

    @Mock
    private DeviceGroupService deviceGroupService;

    @Mock
    private DeviceTagService deviceTagService;

    @Mock
    private DeviceGroupTagAssembler assembler;

    @InjectMocks
    private DeviceController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDevices_should_resolve_group_filter_and_assemble() {
        authenticate();
        DeviceQueryDTO dto = new DeviceQueryDTO();
        dto.setGroupId(3L);
        Page<Device> page = pageWith(new Device());
        when(deviceGroupService.resolveFilterGroupIds(USER_ID, 3L)).thenReturn(List.of(3L, 4L));
        when(deviceService.getDevices(eq(USER_ID), any())).thenReturn(page);

        IPage<Device> result = controller.getDevices(dto).getData();

        assertThat(dto.getOwnerId()).isEqualTo(USER_ID);
        assertThat(dto.getGroupIds()).containsExactly(3L, 4L);
        assertThat(result.getRecords()).hasSize(1);
        verify(assembler).assemble(page.getRecords());
    }

    @Test
    void getDevices_should_validate_tag_filter() {
        authenticate();
        DeviceQueryDTO dto = new DeviceQueryDTO();
        dto.setTagId(7L);
        when(deviceService.getDevices(eq(USER_ID), any())).thenReturn(pageWith(new Device()));

        controller.getDevices(dto);

        verify(deviceTagService).getOwned(USER_ID, 7L);
    }

    @Test
    void getDevices_should_skip_group_tag_resolution_when_absent() {
        authenticate();
        DeviceQueryDTO dto = new DeviceQueryDTO();
        when(deviceService.getDevices(eq(USER_ID), any())).thenReturn(pageWith());

        controller.getDevices(dto);

        verifyNoInteractions(deviceGroupService, deviceTagService);
    }

    @Test
    void getDevices_should_let_admin_keep_owner_filter_unset() {
        authenticateAsAdmin();
        DeviceQueryDTO dto = new DeviceQueryDTO();
        when(deviceService.getDevices(eq(USER_ID), any())).thenReturn(pageWith());

        controller.getDevices(dto);

        assertThat(dto.getOwnerId()).isNull();
    }

    private Page<Device> pageWith(Device... records) {
        Page<Device> page = new Page<>(1, 10);
        page.setRecords(List.of(records));
        return page;
    }

    private void authenticate() {
        authenticateWithRole("USER");
    }

    private void authenticateAsAdmin() {
        authenticateWithRole("ADMIN");
    }

    private void authenticateWithRole(String role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}