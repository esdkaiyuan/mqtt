package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.DebugSimulateRequest;
import com.mqtt.cloud.dto.response.DebugContextResponse;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceDebugService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备调试控制器单测（T-37）。
 * <p>
 * 控制器只做归属校验与转发，因此断言集中在「非归属设备绝不触达下游」与「操作者身份正确透传」。
 */
@ExtendWith(MockitoExtension.class)
class DeviceDebugControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long DEVICE_ID = 101L;
    private static final Long PRODUCT_ID = 202L;
    private static final String DEVICE_KEY = "sensor-001";

    @Mock
    private DeviceService deviceService;
    @Mock
    private ProductService productService;
    @Mock
    private DeviceDebugService deviceDebugService;

    @InjectMocks
    private DeviceDebugController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void context_should_reject_device_of_other_user_and_never_touch_downstream() {
        authenticate(USER_ID, "OPERATOR");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(OTHER_USER_ID));

        assertCode(() -> controller.context(DEVICE_KEY), ResultCode.DEVICE_NOT_OWNED);

        verifyNoInteractions(productService, deviceDebugService);
    }

    @Test
    void context_should_reject_missing_device() {
        authenticate(USER_ID, "OPERATOR");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(null);

        assertCode(() -> controller.context(DEVICE_KEY), ResultCode.DEVICE_NOT_FOUND);
        verifyNoInteractions(productService, deviceDebugService);
    }

    @Test
    void admin_can_access_device_of_other_user() {
        authenticate(USER_ID, "ADMIN");
        Device device = device(OTHER_USER_ID);
        Product product = product();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);
        DebugContextResponse expected = new DebugContextResponse(DEVICE_KEY, "sensor", "env-sensor", "env",
                "MQTT", "SECRET", "ONLINE", true, 3, null, null, List.of(), List.of(), List.of(), List.of());
        when(deviceDebugService.context(device, product)).thenReturn(expected);

        assertThat(controller.context(DEVICE_KEY).getData()).isSameAs(expected);
    }

    @Test
    void simulate_should_pass_current_user_as_operator() throws Exception {
        authenticate(USER_ID, "OPERATOR");
        Device device = device(USER_ID);
        Product product = product();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);

        DebugSimulateRequest request = new DebugSimulateRequest();
        request.setMessageType("data");
        JsonNode payload = objectMapper.readTree("{\"temperature\": 26.5}");
        request.setPayload(payload);

        controller.simulate(DEVICE_KEY, request);

        verify(deviceDebugService).simulate(eq(USER_ID), eq(device), eq(product), eq("data"), eq(payload));
    }

    @Test
    void code_should_forward_target_and_language() {
        authenticate(USER_ID, "OPERATOR");
        Device device = device(USER_ID);
        Product product = product();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product);

        controller.code(DEVICE_KEY, "host", "python");

        verify(deviceDebugService).code(device, product, "host", "python");
    }

    @Test
    void simulate_should_reject_non_owner_before_any_downstream_call() throws Exception {
        authenticate(USER_ID, "VIEWER");
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(OTHER_USER_ID));

        DebugSimulateRequest request = new DebugSimulateRequest();
        request.setMessageType("data");
        request.setPayload(objectMapper.readTree("{}"));

        assertCode(() -> controller.simulate(DEVICE_KEY, request), ResultCode.DEVICE_NOT_OWNED);

        verify(deviceDebugService, never()).simulate(any(), any(), any(), any(), any());
        verifyNoInteractions(productService);
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

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey(DEVICE_KEY);
        device.setDeviceName("sensor");
        device.setProductId(PRODUCT_ID);
        device.setOwnerId(ownerId);
        return device;
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProductKey("env-sensor");
        product.setProductName("env");
        return product;
    }
}
