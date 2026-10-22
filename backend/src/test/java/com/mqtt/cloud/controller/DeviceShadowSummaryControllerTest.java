package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.response.DeviceShadowSummaryVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.DeviceGroupService;
import com.mqtt.cloud.service.DeviceGroupTagAssembler;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceShadowService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import com.mqtt.cloud.service.DeviceTagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多设备影子聚合接口单测（T-42 / G-5-1）：归属隔离与聚合投影。
 */
@ExtendWith(MockitoExtension.class)
class DeviceShadowSummaryControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER = 99L;

    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceShadowService deviceShadowService;

    private DeviceController controller;

    @BeforeEach
    void setUp() {
        controller = new DeviceController(deviceService,
                mock(DeviceStatusHistoryService.class),
                mock(DeviceCredentialService.class),
                mock(DeviceDataService.class),
                mock(com.mqtt.cloud.service.DeviceCommandService.class),
                deviceShadowService,
                mock(com.mqtt.cloud.service.DeviceAccessService.class),
                mock(DeviceGroupService.class),
                mock(DeviceTagService.class),
                mock(DeviceGroupTagAssembler.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void no_ids_should_return_owned_devices_shadow() {
        authenticate(USER_ID, "USER");
        Device device = device(1L, USER_ID, "dev-1");
        when(deviceService.getDevicesByOwner(USER_ID, 200)).thenReturn(List.of(device));
        when(deviceShadowService.getBatch(any())).thenReturn(Map.of(1L,
                new DeviceShadowService.DeviceShadowResponse(
                        true, 3L, Map.of("power", "true"), Map.of("power", "false"),
                        Map.of("power", "true"), LocalDateTime.of(2026, 10, 21, 12, 0))));

        List<DeviceShadowSummaryVO> result = controller.getShadowSummary(null).getData();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeviceKey()).isEqualTo("dev-1");
        assertThat(result.get(0).getVersion()).isEqualTo(3L);
        assertThat(result.get(0).getDelta()).containsEntry("power", "true");
    }

    @Test
    void explicit_ids_should_reject_non_owner_before_shadow_read() {
        authenticate(USER_ID, "USER");
        when(deviceService.getDeviceById(1L)).thenReturn(device(1L, OTHER_USER, "dev-1"));

        assertThatThrownBy(() -> controller.getShadowSummary(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());
        verify(deviceShadowService, never()).getBatch(any());
    }

    @Test
    void admin_can_read_explicit_device_of_other_owner() {
        authenticate(USER_ID, "ADMIN");
        Device device = device(1L, OTHER_USER, "dev-1");
        when(deviceService.getDeviceById(1L)).thenReturn(device);
        when(deviceShadowService.getBatch(any())).thenReturn(Map.of(1L,
                new DeviceShadowService.DeviceShadowResponse(
                        false, 0L, Map.of(), Map.of(), Map.of(), null)));

        assertThat(controller.getShadowSummary(List.of(1L)).getData()).hasSize(1);
    }

    @Test
    void more_than_limit_should_be_rejected() {
        authenticate(USER_ID, "USER");
        List<Long> ids = IntStream.rangeClosed(1, 201).mapToObj(Long::valueOf).toList();

        assertThatThrownBy(() -> controller.getShadowSummary(ids))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }

    private void authenticate(Long userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, "tester", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Device device(Long id, Long ownerId, String key) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerId(ownerId);
        device.setDeviceKey(key);
        device.setDeviceName("设备 " + key);
        device.setProductId(20L);
        device.setStatus("ONLINE");
        return device;
    }
}
