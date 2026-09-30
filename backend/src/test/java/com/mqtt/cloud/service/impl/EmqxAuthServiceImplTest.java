package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmqxAuthServiceImplTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private ProductService productService;
    @Mock
    private DeviceAuthCacheService authCacheService;

    private final DeviceSecretService deviceSecretService = new DeviceSecretServiceImpl();

    private AccessControlProperties properties;
    private EmqxAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AccessControlProperties();
        properties.setEnforceAuth(true);
        properties.setPlatformSecret("platform-secret");
        service = new EmqxAuthServiceImpl(properties, deviceSecretService,
                new DeviceAccessGuard(productService, deviceService, authCacheService));
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

    private DeviceAuthCacheService.AuthMeta meta(Long productId, String productStatus, Integer enabled, String rawSecret) {
        return new DeviceAuthCacheService.AuthMeta(productId, productStatus, enabled, deviceSecretService.hash(rawSecret));
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
    void authenticate_should_skip_db_when_cache_hits() {
        when(authCacheService.get("esp32-fall", "sensor-01"))
                .thenReturn(meta(10L, "ENABLED", 1, "raw-secret"));

        assertThat(service.authenticate("esp32-fall.sensor-01", "raw-secret", "c1")).isTrue();
        assertThat(service.authenticate("esp32-fall.sensor-01", "bad", "c1")).isFalse();
        // 缓存命中即完成判定，产品/设备两次查询都不应发生
        verifyNoInteractions(productService, deviceService);
    }

    @Test
    void authenticate_should_backfill_cache_on_miss() {
        when(authCacheService.get("esp32-fall", "sensor-01")).thenReturn(null);
        when(productService.getByProductKey("esp32-fall")).thenReturn(product("esp32-fall"));
        when(deviceService.getDeviceByKey("sensor-01")).thenReturn(deviceWithSecret("raw-secret"));

        assertThat(service.authenticate("esp32-fall.sensor-01", "raw-secret", "c1")).isTrue();

        verify(authCacheService).put(eq("esp32-fall"), eq("sensor-01"), any());
    }

    @Test
    void authenticate_should_reject_disabled_device_from_cache() {
        when(authCacheService.get("esp32-fall", "sensor-01"))
                .thenReturn(meta(10L, "ENABLED", 0, "raw-secret"));

        assertThat(service.authenticate("esp32-fall.sensor-01", "raw-secret", "c1")).isFalse();
        verifyNoInteractions(productService, deviceService);
    }

    @Test
    void authenticate_should_reject_disabled_product_from_cache() {
        when(authCacheService.get("esp32-fall", "sensor-01"))
                .thenReturn(meta(10L, "DISABLED", 1, "raw-secret"));

        assertThat(service.authenticate("esp32-fall.sensor-01", "raw-secret", "c1")).isFalse();
        verifyNoInteractions(productService, deviceService);
    }

    @Test
    void authenticate_should_reject_unknown_product_or_device() {
        when(productService.getByProductKey("ghost")).thenReturn(null);

        assertThat(service.authenticate("ghost.sensor-01", "x", "c1")).isFalse();
        assertThat(service.authenticate("badformat", "x", "c1")).isFalse();
        // 否定结果不缓存，避免用错误用户名污染缓存
        verify(authCacheService, never()).put(any(), any(), any());
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