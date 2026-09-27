package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmqxAuthServiceImplTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private ProductService productService;

    private final DeviceSecretService deviceSecretService = new DeviceSecretServiceImpl();

    private AccessControlProperties properties;
    private EmqxAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AccessControlProperties();
        properties.setEnforceAuth(true);
        properties.setPlatformSecret("platform-secret");
        service = new EmqxAuthServiceImpl(properties, deviceSecretService, productService, deviceService);
    }

    private Device deviceWithSecret(String rawSecret) {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        device.setEnabled(1);
        device.setDeviceSecretHash(deviceSecretService.hash(rawSecret));
        return device;
    }

    @Test
    void authenticate_should_allow_platform_account() {
        assertThat(service.authenticate("PLATFORM", "platform-secret", "backend-1")).isTrue();
        assertThat(service.authenticate("PLATFORM", "wrong", "backend-1")).isFalse();
    }

    @Test
    void authenticate_should_allow_valid_device() {
        when(productService.getByProductKey("esp32-fall")).thenReturn(product("esp32-fall"));
        when(deviceService.getDeviceByKey("sensor-01")).thenReturn(deviceWithSecret("raw-secret"));

        assertThat(service.authenticate("esp32-fall.sensor-01", "raw-secret", "c1")).isTrue();
        assertThat(service.authenticate("esp32-fall.sensor-01", "bad", "c1")).isFalse();
    }

    @Test
    void authenticate_should_reject_unknown_product_or_device() {
        when(productService.getByProductKey("ghost")).thenReturn(null);

        assertThat(service.authenticate("ghost.sensor-01", "x", "c1")).isFalse();
        assertThat(service.authenticate("badformat", "x", "c1")).isFalse();
    }

    @Test
    void authenticate_should_reject_disabled_device() {
        when(productService.getByProductKey("esp32-fall")).thenReturn(product("esp32-fall"));
        Device device = deviceWithSecret("raw-secret");
        device.setEnabled(0);
        when(deviceService.getDeviceByKey("sensor-01")).thenReturn(device);

        assertThat(service.authenticate("esp32-fall.sensor-01", "raw-secret", "c1")).isFalse();
    }

    @Test
    void authenticate_should_allow_all_when_enforcement_disabled() {
        properties.setEnforceAuth(false);

        assertThat(service.authenticate("anything", "whatever", "c1")).isTrue();
    }

    private Product product(String key) {
        Product p = new Product();
        p.setId(10L);
        p.setProductKey(key);
        p.setStatus("ENABLED");
        return p;
    }
}