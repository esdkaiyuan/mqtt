package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.GroupPermission;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.CommandInvokeRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.DeviceEventRecord;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceAccessService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备物模型数据只读接口单测（T-14 实施计划 P5）。
 * <p>
 * 聚焦「归属校验 + 参数透传」：越权返回 2003、设备不存在返回设备不存在码、ADMIN 可跨用户、
 * 分页参数按原样下传。鉴权注解与 HTTP 状态码由 {@code SecurityConfig} 与全局异常处理器保证。
 */
@ExtendWith(MockitoExtension.class)
class DeviceControllerDataTest {

    private static final String DEVICE_KEY = "esp32-fall-001";

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceStatusHistoryService deviceStatusHistoryService;

    @Mock
    private DeviceCredentialService deviceCredentialService;

    @Mock
    private DeviceDataService deviceDataService;

    @Mock
    private com.mqtt.cloud.service.DeviceAccessService deviceAccessService;

    @Mock
    private DeviceCommandService deviceCommandService;

    @InjectMocks
    private DeviceController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void properties_should_return_owned_device_data() {
        authenticate(10L, "USER");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(100L, 10L));
        DevicePropertyLatest property = new DevicePropertyLatest();
        property.setIdentifier("temperature");
        when(deviceDataService.getPropertyLatest(100L)).thenReturn(List.of(property));

        Result<List<DevicePropertyLatest>> result = controller.getDeviceProperties(DEVICE_KEY);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).extracting(DevicePropertyLatest::getIdentifier).containsExactly("temperature");
    }

    @Test
    void properties_should_reject_non_owner() {
        authenticate(10L, "USER");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(100L, 999L));

        assertThatThrownBy(() -> controller.getDeviceProperties(DEVICE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());
        verify(deviceDataService, never()).getPropertyLatest(anyLong());
    }

    @Test
    void properties_should_allow_admin_across_owners() {
        authenticate(1L, "ADMIN");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(100L, 999L));
        when(deviceDataService.getPropertyLatest(100L)).thenReturn(List.of());

        assertThat(controller.getDeviceProperties(DEVICE_KEY).getCode()).isEqualTo(200);
    }

    @Test
    void properties_should_report_device_not_found() {
        authenticate(10L, "USER");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(null);

        assertThatThrownBy(() -> controller.getDeviceProperties(DEVICE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_FOUND.getCode());
    }

    @Test
    void events_should_pass_pagination_through() {
        authenticate(10L, "USER");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(100L, 10L));
        when(deviceDataService.getEvents(100L, 2L, 50L)).thenReturn(new Page<>(2, 50));

        Result<IPage<DeviceEventRecord>> result = controller.getDeviceEvents(DEVICE_KEY, 2L, 50L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(deviceDataService).getEvents(100L, 2L, 50L);
    }

    @Test
    void events_should_reject_non_owner() {
        authenticate(10L, "USER");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(100L, 999L));

        assertThatThrownBy(() -> controller.getDeviceEvents(DEVICE_KEY, 1L, 20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());
        verify(deviceDataService, never()).getEvents(anyLong(), anyLong(), anyLong());
    }

    @Test
    void properties_should_allow_shared_view_member() {
        authenticate(10L, "VIEWER");
        Device device = device(100L, 999L);
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device);
        when(deviceAccessService.resolveDeviceLevel(10L, "VIEWER", 100L))
                .thenReturn(GroupPermission.VIEW);
        when(deviceDataService.getPropertyLatest(100L)).thenReturn(List.of());

        assertThat(controller.getDeviceProperties(DEVICE_KEY).getCode()).isEqualTo(200);
        assertThat(device.getAccessLevel()).isEqualTo(GroupPermission.VIEW);
    }

    @Test
    void commands_should_reject_view_member() {
        authenticate(10L, "OPERATOR");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(100L, 999L));
        when(deviceAccessService.resolveDeviceLevel(10L, "OPERATOR", 100L))
                .thenReturn(GroupPermission.VIEW);

        assertThatThrownBy(() -> controller.sendCommand(DEVICE_KEY, commandRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());
        verify(deviceCommandService, never()).invoke(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void commands_should_allow_operate_member() {
        authenticate(10L, "OPERATOR");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(100L, 999L));
        when(deviceAccessService.resolveDeviceLevel(10L, "OPERATOR", 100L))
                .thenReturn(GroupPermission.OPERATE);
        DeviceCommandRecord record = new DeviceCommandRecord();
        record.setCommandId("cmd-1");
        when(deviceCommandService.invoke(org.mockito.ArgumentMatchers.any())).thenReturn(record);

        assertThat(controller.sendCommand(DEVICE_KEY, commandRequest()).getData())
                .isSameAs(record);
    }

    private CommandInvokeRequest commandRequest() {
        CommandInvokeRequest request = new CommandInvokeRequest();
        request.setType(DeviceCommandService.TYPE_PROPERTY_SET);
        request.setIdentifier("power");
        return request;
    }

    private Device device(Long id, Long ownerId) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey(DEVICE_KEY);
        device.setOwnerId(ownerId);
        device.setDeleted(0);
        return device;
    }

    private void authenticate(Long userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, "user-" + userId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}