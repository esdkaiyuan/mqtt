package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.HmiSaveDTO;
import com.mqtt.cloud.dto.response.HmiDraftVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.HmiViewService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 快速上位机控制器单测（T-38）：归属校验与设备 / 产品透传。
 */
@ExtendWith(MockitoExtension.class)
class HmiViewControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long DEVICE_ID = 11L;
    private static final Long PRODUCT_ID = 20L;

    @Mock
    private HmiViewService hmiViewService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private ProductService productService;

    @InjectMocks
    private HmiViewController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_should_reject_device_of_other_user_before_service_call() {
        authenticate(USER_ID, "OPERATOR");
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(OTHER_USER_ID));

        assertCode(() -> controller.create(dto()), ResultCode.DEVICE_NOT_OWNED);

        verifyNoInteractions(productService, hmiViewService);
    }

    @Test
    void create_should_pass_resolved_device_and_product() {
        authenticate(USER_ID, "OPERATOR");
        Device device = device(USER_ID);
        Product product = product();
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);

        controller.create(dto());

        verify(hmiViewService).create(eq(USER_ID), eq(device), eq(product), eq(dto()));
    }

    @Test
    void draft_should_reject_missing_device() {
        authenticate(USER_ID, "OPERATOR");
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(null);

        assertCode(() -> controller.draft(DEVICE_ID), ResultCode.DEVICE_NOT_FOUND);
        verify(hmiViewService, never()).draft(any(), any());
    }

    @Test
    void draft_should_return_service_result() {
        authenticate(USER_ID, "OPERATOR");
        Device device = device(USER_ID);
        Product product = product();
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);
        HmiDraftVO expected = new HmiDraftVO(DEVICE_ID, "sensor-001", "温湿度传感器",
                "温湿度传感器上位机", null, java.util.List.of());
        when(hmiViewService.draft(device, product)).thenReturn(expected);

        assertThat(controller.draft(DEVICE_ID).getData()).isSameAs(expected);
    }

    @Test
    void admin_can_create_for_device_of_other_user() {
        authenticate(USER_ID, "ADMIN");
        Device device = device(OTHER_USER_ID);
        Product product = product();
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);

        controller.create(dto());

        verify(hmiViewService).create(eq(USER_ID), eq(device), eq(product), eq(dto()));
    }

    private void authenticate(Long userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, "tester", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ResultCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private HmiSaveDTO dto() {
        HmiSaveDTO dto = new HmiSaveDTO();
        dto.setName("面板");
        dto.setDeviceId(DEVICE_ID);
        dto.setConfig("{\"widgets\":[{\"type\":\"events\"}]}");
        return dto;
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey("sensor-001");
        device.setDeviceName("温湿度传感器");
        device.setProductId(PRODUCT_ID);
        device.setOwnerId(ownerId);
        return device;
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProductKey("env-sensor");
        return product;
    }
}
