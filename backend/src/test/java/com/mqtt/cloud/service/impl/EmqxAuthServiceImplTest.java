package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.constant.AuthModeValue;
import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCertificateService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceTokenService;
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

/**
 * EMQX 认证回调单测（T-25）。
 * <p>
 * 三处判定被刻意拆开覆盖：准入前置（产品启用、设备启用）对三种认证方式一致，
 * 凭据验证按产品 {@code auth_mode} 二分派，证书与令牌的具体实现各自有独立单测，
 * 这里只验证分派与前置顺序是否正确。
 */
@ExtendWith(MockitoExtension.class)
class EmqxAuthServiceImplTest {

    private static final String PRODUCT_KEY = "esp32-fall";
    private static final String DEVICE_KEY = "sensor-01";
    private static final String USERNAME = "esp32-fall.sensor-01";
    private static final String CN = "esp32-fall.sensor-01";

    @Mock
    private DeviceService deviceService;
    @Mock
    private ProductService productService;
    @Mock
    private DeviceAuthCacheService authCacheService;
    @Mock
    private DeviceCertificateService deviceCertificateService;
    @Mock
    private DeviceTokenService deviceTokenService;

    private final DeviceSecretService deviceSecretService = new DeviceSecretServiceImpl();

    private AccessControlProperties properties;
    private EmqxAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AccessControlProperties();
        properties.setEnforceAuth(true);
        properties.setPlatformSecret("platform-secret");
        service = new EmqxAuthServiceImpl(properties, deviceSecretService,
                new DeviceAccessGuard(productService, deviceService, authCacheService),
                deviceCertificateService, deviceTokenService);
    }

    private Device deviceWithSecret(String rawSecret) {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey(DEVICE_KEY);
        device.setProductId(10L);
        device.setEnabled(1);
        device.setDeviceSecretHash(deviceSecretService.hash(rawSecret));
        return device;
    }

    private DeviceAuthCacheService.AuthMeta meta(Long productId, String productStatus, Integer enabled, String rawSecret) {
        return meta(productId, AuthModeValue.SECRET, productStatus, enabled, rawSecret, null);
    }

    private DeviceAuthCacheService.AuthMeta meta(Long productId, String authMode, String productStatus,
                                                 Integer enabled, String rawSecret, Long deviceId) {
        return new DeviceAuthCacheService.AuthMeta(productId, deviceId == null ? 1L : deviceId, productStatus,
                enabled, authMode, rawSecret == null ? null : deviceSecretService.hash(rawSecret), null,
                "DIRECT", null);
    }

    @Test
    void authenticate_should_allow_platform_account() {
        assertThat(service.authenticate("PLATFORM", "platform-secret", "backend-1", "", "")).isTrue();
        assertThat(service.authenticate("PLATFORM", "wrong", "backend-1", "", "")).isFalse();
    }

    @Test
    void authenticate_should_allow_valid_device() {
        when(productService.getByProductKey(PRODUCT_KEY)).thenReturn(product(PRODUCT_KEY));
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(deviceWithSecret("raw-secret"));

        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", "", "")).isTrue();
        assertThat(service.authenticate(USERNAME, "bad", "c1", "", "")).isFalse();
    }

    @Test
    void authenticate_should_skip_db_when_cache_hits() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, "ENABLED", 1, "raw-secret"));

        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", "", "")).isTrue();
        assertThat(service.authenticate(USERNAME, "bad", "c1", "", "")).isFalse();
        // 缓存命中即完成判定，产品/设备两次查询都不应发生
        verifyNoInteractions(productService, deviceService);
    }

    @Test
    void authenticate_should_backfill_cache_on_miss() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY)).thenReturn(null);
        when(productService.getByProductKey(PRODUCT_KEY)).thenReturn(product(PRODUCT_KEY));
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(deviceWithSecret("raw-secret"));

        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", "", "")).isTrue();

        verify(authCacheService).put(eq(PRODUCT_KEY), eq(DEVICE_KEY), any());
    }

    @Test
    void authenticate_should_fall_back_to_secret_when_auth_mode_absent() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, null, "ENABLED", 1, "raw-secret", null));

        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", "", "")).isTrue();
        assertThat(service.authenticate(USERNAME, "bad", "c1", "", "")).isFalse();
        verifyNoInteractions(deviceCertificateService, deviceTokenService);
    }

    @Test
    void authenticate_should_reject_disabled_device_from_cache() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, "ENABLED", 0, "raw-secret"));

        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", "", "")).isFalse();
        verifyNoInteractions(productService, deviceService);
    }

    @Test
    void authenticate_should_reject_disabled_product_from_cache() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, "DISABLED", 1, "raw-secret"));

        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", "", "")).isFalse();
        verifyNoInteractions(productService, deviceService);
    }

    @Test
    void authenticate_should_reject_unknown_product_or_device() {
        when(productService.getByProductKey("ghost")).thenReturn(null);

        assertThat(service.authenticate("ghost.sensor-01", "x", "c1", "", "")).isFalse();
        assertThat(service.authenticate("badformat", "x", "c1", "", "")).isFalse();
        // 否定结果不缓存，避免用错误用户名污染缓存
        verify(authCacheService, never()).put(any(), any(), any());
    }

    @Test
    void authenticate_should_reject_disabled_device() {
        when(productService.getByProductKey(PRODUCT_KEY)).thenReturn(product(PRODUCT_KEY));
        Device device = deviceWithSecret("raw-secret");
        device.setEnabled(0);
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device);

        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", "", "")).isFalse();
    }

    @Test
    void authenticate_should_allow_all_when_enforcement_disabled() {
        properties.setEnforceAuth(false);

        assertThat(service.authenticate("anything", "whatever", "c1", "", "")).isTrue();
    }

    @Test
    void authenticate_should_allow_x509_device_with_matching_cn() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, AuthModeValue.X509, "ENABLED", 1, null, 1L));
        when(deviceCertificateService.isRevoked(1L)).thenReturn(false);

        assertThat(service.authenticate(USERNAME, "", "c1", "CN=" + CN + ",O=MQTT Cloud", CN)).isTrue();
        // 该模式不比对设备密钥，口令位即使有值也不参与判定
        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void authenticate_should_reject_x509_device_when_cn_mismatch() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, AuthModeValue.X509, "ENABLED", 1, null, 1L));

        assertThat(service.authenticate(USERNAME, "", "c1", "", "esp32-fall.sensor-99")).isFalse();
        // CN 不符即拒，不必再查吊销态
        verify(deviceCertificateService, never()).isRevoked(any());
    }

    @Test
    void authenticate_should_reject_x509_device_when_common_name_absent() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, AuthModeValue.X509, "ENABLED", 1, null, 1L));

        // 非 TLS 连接下 EMQX 传空串，不得退化为口令校验
        assertThat(service.authenticate(USERNAME, "", "c1", "", "")).isFalse();
        assertThat(service.authenticate(USERNAME, "raw-secret", "c1", null, null)).isFalse();
        verify(deviceCertificateService, never()).isRevoked(any());
    }

    @Test
    void authenticate_should_reject_x509_device_when_certificate_revoked() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, AuthModeValue.X509, "ENABLED", 1, null, 1L));
        when(deviceCertificateService.isRevoked(1L)).thenReturn(true);

        assertThat(service.authenticate(USERNAME, "", "c1", "CN=" + CN, CN)).isFalse();
    }

    @Test
    void authenticate_should_allow_jwt_device_when_token_verifies() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, AuthModeValue.JWT, "ENABLED", 1, null, 1L));
        when(deviceTokenService.verify("device-token", USERNAME)).thenReturn(true);

        assertThat(service.authenticate(USERNAME, "device-token", "c1", "", "")).isTrue();
    }

    @Test
    void authenticate_should_reject_jwt_device_when_token_invalid() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, AuthModeValue.JWT, "ENABLED", 1, null, 1L));
        when(deviceTokenService.verify("bad-token", USERNAME)).thenReturn(false);

        assertThat(service.authenticate(USERNAME, "bad-token", "c1", "", "")).isFalse();
    }

    @Test
    void authenticate_should_reject_disabled_device_before_credential_check() {
        when(authCacheService.get(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(meta(10L, AuthModeValue.X509, "ENABLED", 0, null, 1L));

        // 设备已禁用时三种方式同拒，凭据验证不再执行
        assertThat(service.authenticate(USERNAME, "", "c1", "CN=" + CN, CN)).isFalse();
        verifyNoInteractions(deviceCertificateService, deviceTokenService);
    }

    private Product product(String key) {
        Product p = new Product();
        p.setId(10L);
        p.setProductKey(key);
        p.setStatus("ENABLED");
        return p;
    }
}
