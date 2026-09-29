package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialResetServiceImplTest {

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private ProductService productService;
    @Mock
    private DeviceAuthCacheService authCacheService;

    private final DeviceSecretService deviceSecretService = new DeviceSecretServiceImpl();

    private DeviceCredentialResetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCredentialResetServiceImpl(deviceMapper, productService, deviceSecretService, authCacheService);
    }

    @Test
    void resetSecret_should_store_hash_return_plaintext_once_and_evict_cache() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(productService.requireById(10L)).thenReturn(product("esp32-fall"));

        String plaintext = service.resetSecret(1L);

        assertThat(plaintext).hasSize(32);
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).updateById(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getDeviceSecretHash()).startsWith("$2");
        assertThat(saved.getSecretUpdatedAt()).isNotNull();
        assertThat(deviceSecretService.matches(plaintext, saved.getDeviceSecretHash())).isTrue();
        // 轮换后必须立即失效，避免旧哈希在 TTL 内继续放行
        verify(authCacheService).evict("esp32-fall", "sensor-01");
    }

    @Test
    void resetSecret_should_reject_missing_device() {
        when(deviceMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.resetSecret(404L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resetPage_should_return_product_device_username_and_rotated_secret() {
        Device device = new Device();
        device.setId(7L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        when(productService.requireById(10L)).thenReturn(product("esp32-fall"));

        List<String[]> rows = service.resetPage(List.of(device));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(4);
        assertThat(rows.get(0)[0]).isEqualTo("esp32-fall");
        assertThat(rows.get(0)[1]).isEqualTo("sensor-01");
        assertThat(rows.get(0)[2]).isEqualTo("esp32-fall.sensor-01");
        assertThat(rows.get(0)[3]).hasSize(32);
        verify(authCacheService).evict("esp32-fall", "sensor-01");
    }

    private Product product(String key) {
        Product p = new Product();
        p.setId(10L);
        p.setProductKey(key);
        return p;
    }
}