package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialServiceImplTest {

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private ProductService productService;

    private final DeviceSecretService deviceSecretService = new DeviceSecretServiceImpl();

    private DeviceCredentialServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCredentialServiceImpl(deviceMapper, productService, deviceSecretService);
    }

    @Test
    void resetSecret_should_store_hash_and_return_plaintext_once() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        String plaintext = service.resetSecret(1L);

        assertThat(plaintext).hasSize(32);
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).updateById(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getDeviceSecretHash()).startsWith("$2");
        assertThat(saved.getSecretUpdatedAt()).isNotNull();
        assertThat(deviceSecretService.matches(plaintext, saved.getDeviceSecretHash())).isTrue();
    }

    @Test
    void exportCredentials_should_return_product_device_username_and_rotated_secret() {
        Device device = new Device();
        device.setId(7L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        when(deviceMapper.selectList(any())).thenReturn(List.of(device));
        when(deviceMapper.selectById(7L)).thenReturn(device);
        when(productService.requireById(10L)).thenReturn(product("esp32-fall"));

        List<String[]> rows = service.exportCredentials();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(4);
        assertThat(rows.get(0)[0]).isEqualTo("esp32-fall");
        assertThat(rows.get(0)[1]).isEqualTo("sensor-01");
        assertThat(rows.get(0)[2]).isEqualTo("esp32-fall.sensor-01");
        assertThat(rows.get(0)[3]).hasSize(32);
    }

    private Product product(String key) {
        Product p = new Product();
        p.setId(10L);
        p.setProductKey(key);
        return p;
    }
}