package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAccessGuardTest {

    @Mock
    private ProductService productService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceAuthCacheService authCacheService;

    private DeviceAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DeviceAccessGuard(productService, deviceService, authCacheService);
    }

    private DeviceAuthCacheService.AuthMeta meta(String productStatus, Integer deviceEnabled) {
        return new DeviceAuthCacheService.AuthMeta(10L, productStatus, deviceEnabled, "hash");
    }

    @Test
    void resolve_should_prefer_cache_and_skip_db() {
        when(authCacheService.get("esp32-fall", "sensor-01")).thenReturn(meta("ENABLED", 1));

        assertThat(guard.resolve("esp32-fall", "sensor-01")).isNotNull();

        verifyNoInteractions(productService, deviceService);
    }

    @Test
    void resolve_should_backfill_cache_on_miss() {
        when(authCacheService.get("esp32-fall", "sensor-01")).thenReturn(null);
        Product product = product(10L, "esp32-fall", "ENABLED");
        when(productService.getByProductKey("esp32-fall")).thenReturn(product);
        when(deviceService.getDeviceByKey("sensor-01")).thenReturn(device(10L, 1));

        assertThat(guard.resolve("esp32-fall", "sensor-01")).isNotNull();

        verify(authCacheService).put(any(), any(), any());
    }

    /** 否定结果不缓存：否则一个错误用户名会在 TTL 内持续污染后续判定。 */
    @Test
    void resolve_should_not_cache_negative_result() {
        when(authCacheService.get("ghost", "sensor-01")).thenReturn(null);
        when(productService.getByProductKey("ghost")).thenReturn(null);

        assertThat(guard.resolve("ghost", "sensor-01")).isNull();

        verify(authCacheService, never()).put(any(), any(), any());
    }

    /** 设备存在但不属于该产品：用户名可被伪造，必须按产品归属校验。 */
    @Test
    void resolve_should_reject_device_belonging_to_another_product() {
        when(authCacheService.get("esp32-fall", "sensor-01")).thenReturn(null);
        when(productService.getByProductKey("esp32-fall")).thenReturn(product(10L, "esp32-fall", "ENABLED"));
        when(deviceService.getDeviceByKey("sensor-01")).thenReturn(device(99L, 1));

        assertThat(guard.resolve("esp32-fall", "sensor-01")).isNull();

        verify(authCacheService, never()).put(any(), any(), any());
    }

    @Test
    void isPermitted_should_require_enabled_product_and_device() {
        assertThat(guard.isPermitted(meta("ENABLED", 1))).isTrue();

        assertThat(guard.isPermitted(meta("ENABLED", 0))).isFalse();
        assertThat(guard.isPermitted(meta("DISABLED", 1))).isFalse();
        assertThat(guard.isPermitted(meta("ENABLED", null))).isFalse();
        assertThat(guard.isPermitted(null)).isFalse();
    }

    private Product product(Long id, String key, String status) {
        Product p = new Product();
        p.setId(id);
        p.setProductKey(key);
        p.setStatus(status);
        return p;
    }

    private Device device(Long productId, Integer enabled) {
        Device d = new Device();
        d.setId(1L);
        d.setDeviceKey("sensor-01");
        d.setProductId(productId);
        d.setEnabled(enabled);
        d.setDeviceSecretHash("hash");
        return d;
    }
}