package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.ProductMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.EmqxClientKicker;
import com.mqtt.cloud.service.PayloadCodecService;
import com.mqtt.cloud.service.TopicPrefixResolver;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @org.mockito.Mock
    private com.mqtt.cloud.mapper.DeviceMapper deviceMapper;

    @Mock
    private DeviceAuthCacheService authCacheService;

    @Mock
    private DeviceSecretService deviceSecretService;

    @Mock
    private EmqxClientKicker emqxClientKicker;

    @Mock
    private PayloadCodecService payloadCodecService;

    /** 模板校验属纯函数逻辑，用真实实例而非 mock，避免断言退化为复制桩返回值。 */
    @Spy
    private TopicPrefixResolver topicPrefixResolver = new TopicPrefixResolver();

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 需要实体的 TableInfo；纯单测无 MyBatis 上下文，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Device.class);
    }

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
        assertThat(created.getProtocolType()).isEqualTo("MQTT");
        assertThat(created.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void create_should_normalize_payload_codec() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        when(payloadCodecService.validateAndNormalize("{\"template\":{}}"))
                .thenReturn("{\"version\":1,\"template\":{}}");
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });
        ProductRequest req = request("codec-product");
        req.setPayloadCodec("{\"template\":{}}");

        Product created = productService.create(req);

        assertThat(created.getPayloadCodec()).isEqualTo("{\"version\":1,\"template\":{}}");
        verify(payloadCodecService).validateAndNormalize("{\"template\":{}}");
    }

    @Test
    void create_should_persist_modbus_driver_config() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> 1);
        ProductRequest req = request("modbus-plc");
        req.setProtocolType("MODBUS");
        req.setDriverId("modbus-tcp");
        req.setDriverConfig("{\"host\":\"192.168.10.20\",\"port\":502,\"unitId\":1}");

        Product created = productService.create(req);

        assertThat(created.getProtocolType()).isEqualTo("MODBUS");
        assertThat(created.getDriverId()).isEqualTo("modbus-tcp");
        assertThat(created.getDriverConfig()).contains("192.168.10.20");
    }

    @Test
    void create_should_reject_unsupported_protocol() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        ProductRequest req = request("serial-device");
        req.setProtocolType("SERIAL");

        assertThatThrownBy(() -> productService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的接入协议");
    }

    @Test
    void update_without_protocol_should_keep_existing_driver_config() {
        Product existing = product(9L, "modbus-plc", "ENABLED");
        existing.setProtocolType("MODBUS");
        existing.setDriverId("modbus-tcp");
        existing.setDriverConfig("{\"host\":\"192.168.10.20\"}");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);
        ProductRequest req = request("modbus-plc");

        productService.update(9L, req);

        assertThat(existing.getProtocolType()).isEqualTo("MODBUS");
        assertThat(existing.getDriverId()).isEqualTo("modbus-tcp");
        assertThat(existing.getDriverConfig()).contains("192.168.10.20");
    }

    @Test
    void update_switching_to_mqtt_should_clear_driver_config() {
        Product existing = product(9L, "modbus-plc", "ENABLED");
        existing.setProtocolType("MODBUS");
        existing.setDriverId("modbus-tcp");
        existing.setDriverConfig("{\"host\":\"192.168.10.20\"}");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);
        ProductRequest req = request("modbus-plc");
        req.setProtocolType("MQTT");

        productService.update(9L, req);

        assertThat(existing.getProtocolType()).isEqualTo("MQTT");
        assertThat(existing.getDriverId()).isNull();
        assertThat(existing.getDriverConfig()).isNull();
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
    void disableProduct_should_set_status_evict_auth_cache_and_kick_devices() {
        Product existing = product(9L, "esp32-fall", "ENABLED");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(deviceMapper.selectList(any())).thenReturn(List.of(
                device(101L, "sensor-01"), device(102L, "sensor-02")));
        when(deviceSecretService.buildUsername("esp32-fall", "sensor-01")).thenReturn("esp32-fall.sensor-01");
        when(deviceSecretService.buildUsername("esp32-fall", "sensor-02")).thenReturn("esp32-fall.sensor-02");

        productService.disableProduct(9L);

        assertThat(existing.getStatus()).isEqualTo("DISABLED");
        verify(productMapper).updateById(existing);
        verify(authCacheService).evictProduct("esp32-fall");
        verify(emqxClientKicker).kickByUsername("esp32-fall.sensor-01");
        verify(emqxClientKicker).kickByUsername("esp32-fall.sensor-02");
    }

    @Test
    void disableProduct_should_be_idempotent_when_already_disabled() {
        Product existing = product(9L, "esp32-fall", "DISABLED");
        when(productMapper.selectById(9L)).thenReturn(existing);

        productService.disableProduct(9L);

        verify(productMapper, never()).updateById(any(Product.class));
        verifyNoInteractions(authCacheService);
        verifyNoInteractions(emqxClientKicker);
    }

    @Test
    void enableProduct_should_set_status_and_evict_auth_cache() {
        Product existing = product(9L, "esp32-fall", "DISABLED");
        when(productMapper.selectById(9L)).thenReturn(existing);

        productService.enableProduct(9L);

        assertThat(existing.getStatus()).isEqualTo("ENABLED");
        verify(productMapper).updateById(existing);
        verify(authCacheService).evictProduct("esp32-fall");
        verifyNoInteractions(emqxClientKicker);
    }

    private Product product(Long id, String key, String status) {
        Product product = new Product();
        product.setId(id);
        product.setProductKey(key);
        product.setStatus(status);
        return product;
    }

    private Device device(Long id, String key) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey(key);
        return device;
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

    @Test
    void create_should_use_provided_auth_mode() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        ProductRequest req = request("esp32-fall");
        req.setAuthMode("X509");

        Product created = productService.create(req);

        assertThat(created.getAuthMode()).isEqualTo("X509");
    }

    @Test
    void create_should_reject_unsupported_auth_mode() {
        ProductRequest req = request("esp32-fall");
        req.setAuthMode("TLS");

        assertThatThrownBy(() -> productService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的认证方式");
    }

    @Test
    void create_should_reject_invalid_topic_prefix() {
        ProductRequest req = request("esp32-fall");
        req.setTopicPrefix("device/+/data");

        assertThatThrownBy(() -> productService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主题前缀模板");
    }

    @Test
    void update_should_evict_auth_cache_when_auth_mode_changes() {
        Product existing = product(9L, "esp32-fall", "ENABLED");
        existing.setAuthMode("SECRET");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);
        ProductRequest req = request("esp32-fall");
        req.setAuthMode("JWT");

        productService.update(9L, req);

        assertThat(existing.getAuthMode()).isEqualTo("JWT");
        verify(authCacheService).evictProduct("esp32-fall");
    }

    @Test
    void update_should_evict_auth_cache_when_topic_prefix_changes() {
        Product existing = product(9L, "esp32-fall", "ENABLED");
        existing.setAuthMode("SECRET");
        existing.setTopicPrefix("device/{deviceKey}");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);
        ProductRequest req = request("esp32-fall");
        req.setTopicPrefix("site/{productKey}/{deviceKey}");

        productService.update(9L, req);

        assertThat(existing.getTopicPrefix()).isEqualTo("site/{productKey}/{deviceKey}");
        verify(authCacheService).evictProduct("esp32-fall");
    }

    @Test
    void update_should_not_evict_auth_cache_when_auth_mode_and_prefix_absent() {
        Product existing = product(9L, "esp32-fall", "ENABLED");
        existing.setAuthMode("SECRET");
        existing.setTopicPrefix("device/{deviceKey}");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);

        ProductRequest req = request("esp32-fall");
        req.setTopicPrefix("device/{deviceKey}");

        productService.update(9L, req);

        assertThat(existing.getAuthMode()).isEqualTo("SECRET");
        verifyNoInteractions(authCacheService);
    }

    @Test
    void update_should_reject_unsupported_auth_mode() {
        Product existing = product(9L, "esp32-fall", "ENABLED");
        when(productMapper.selectById(9L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);
        ProductRequest req = request("esp32-fall");
        req.setAuthMode("SECRET2");

        assertThatThrownBy(() -> productService.update(9L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的认证方式");
    }
}