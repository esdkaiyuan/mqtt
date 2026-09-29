package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.ProductMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @org.mockito.Mock
    private com.mqtt.cloud.mapper.DeviceMapper deviceMapper;

    @Mock
    private DeviceAuthCacheService authCacheService;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductRequest request(String key) {
        ProductRequest req = new ProductRequest();
        req.setProductKey(key);
        req.setProductName("测试产品");
        return req;
    }

    @Test
    void create_should_reject_duplicate_product_key() {
        when(productMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> productService.create(request("dup-key")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("产品标识已存在");
    }

    @Test
    void create_should_default_topic_prefix_and_format() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });

        Product created = productService.create(request("esp32-fall"));

        assertThat(created.getTopicPrefix()).isEqualTo("device/{deviceKey}");
        assertThat(created.getPayloadFormat()).isEqualTo("JSON");
        assertThat(created.getAuthMode()).isEqualTo("SECRET");
        assertThat(created.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void delete_should_reject_when_product_has_devices() {
        Product existing = new Product();
        existing.setId(9L);
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(deviceMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> productService.delete(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该产品下存在设备");
    }

    @Test
    void delete_should_evict_product_auth_cache() {
        Product existing = new Product();
        existing.setId(9L);
        existing.setProductKey("esp32-fall");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(deviceMapper.selectCount(any())).thenReturn(0L);

        productService.delete(9L);

        verify(authCacheService).evictProduct("esp32-fall");
    }

    @Test
    void update_should_evict_old_product_key_when_key_changes() {
        Product existing = new Product();
        existing.setId(9L);
        existing.setProductKey("old-key");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);

        productService.update(9L, request("new-key"));

        verify(authCacheService).evictProduct("old-key");
    }
}