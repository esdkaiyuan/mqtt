package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.AuthModeValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.AuthExtensionProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceTokenService.DeviceToken;
import com.mqtt.cloud.service.ProductService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/**
 * 设备令牌服务单测（T-25）。
 * <p>
 * 与证书服务不同，令牌链路不需要 CA 材料，只需一份独立的签名密钥，
 * 因此这里不生成证书，直接覆盖签发前置校验与验签的四类失败：过期、主体不符、异密钥、篡改。
 */
@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceImplTest {

    private static final String PRODUCT_KEY = "esp32-fall";
    private static final String DEVICE_KEY = "sensor-01";
    private static final String USERNAME = "esp32-fall.sensor-01";
    /** 含连字符，Base64 解码必定失败，走 UTF-8 回落分支，同时长度足够 HS256 的 32 字节下限 */
    private static final String SIGNING_SECRET = "t25-device-token-secret-0123456789-abcdefghij";
    private static final String OTHER_SECRET = "t25-other-device-secret-9876543210-zyxwvutsrq";

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private ProductService productService;

    private final DeviceSecretService deviceSecretService = new DeviceSecretServiceImpl();

    private AuthExtensionProperties properties;
    private DeviceTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AuthExtensionProperties();
        properties.getJwt().setEnabled(true);
        properties.getJwt().setSecret(SIGNING_SECRET);
        properties.getJwt().setTtlSeconds(3600);
        service = new DeviceTokenServiceImpl(deviceMapper, productService, deviceSecretService, properties);
    }

    @Test
    void issueToken_should_bind_subject_to_device_username() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, 0));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.JWT));

        DeviceToken issued = service.issueToken(1L);

        assertThat(issued.deviceId()).isEqualTo(1L);
        assertThat(issued.username()).isEqualTo(USERNAME);
        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(LocalDateTime.now());
        // 令牌自洽：由本服务签发即可通过校验，换用户名或换密钥都不通过
        assertThat(service.verify(issued.token(), USERNAME)).isTrue();
        assertThat(service.verify(issued.token(), "esp32-fall.sensor-99")).isFalse();
    }

    @Test
    void issueToken_should_reject_when_jwt_disabled() {
        properties.getJwt().setEnabled(false);
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, 0));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.JWT));

        BusinessException error = catchThrowableOfType(() -> service.issueToken(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.AUTH_MODE_UNSUPPORTED);
    }

    @Test
    void issueToken_should_reject_when_product_not_in_jwt_mode() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, 0));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.SECRET));

        BusinessException error = catchThrowableOfType(() -> service.issueToken(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.AUTH_MODE_UNSUPPORTED);
    }

    @Test
    void issueToken_should_reject_unknown_device() {
        when(deviceMapper.selectById(1L)).thenReturn(null);

        BusinessException error = catchThrowableOfType(() -> service.issueToken(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.DEVICE_NOT_FOUND);
    }

    @Test
    void issueToken_should_reject_soft_deleted_device() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, 1));

        BusinessException error = catchThrowableOfType(() -> service.issueToken(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.DEVICE_NOT_FOUND);
    }

    @Test
    void issueToken_should_fail_when_signing_secret_absent() {
        properties.getJwt().setSecret(" ");
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, 0));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.JWT));

        BusinessException error = catchThrowableOfType(() -> service.issueToken(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.DEVICE_TOKEN_ISSUE_FAILED);
    }

    @Test
    void verify_should_reject_token_signed_with_other_secret() {
        String foreign = Jwts.builder()
                .subject(USERNAME)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(service.verify(foreign, USERNAME)).isFalse();
    }

    @Test
    void verify_should_reject_expired_token() {
        properties.getJwt().setTtlSeconds(-60);
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, 0));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.JWT));

        String expired = service.issueToken(1L).token();

        assertThat(service.verify(expired, USERNAME)).isFalse();
    }

    @Test
    void verify_should_reject_tampered_token() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, 0));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.JWT));
        String token = service.issueToken(1L).token();

        assertThat(service.verify(token + "x", USERNAME)).isFalse();
        assertThat(service.verify("not-a-jwt", USERNAME)).isFalse();
    }

    @Test
    void verify_should_reject_blank_token_or_username() {
        assertThat(service.verify(null, USERNAME)).isFalse();
        assertThat(service.verify("", USERNAME)).isFalse();
        assertThat(service.verify(" ", USERNAME)).isFalse();
    }

    private static Device device(Long id, Integer deleted) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey(DEVICE_KEY);
        device.setProductId(10L);
        device.setEnabled(1);
        device.setDeleted(deleted);
        return device;
    }

    private static Product product(String productKey, String authMode) {
        Product product = new Product();
        product.setId(10L);
        product.setProductKey(productKey);
        product.setAuthMode(authMode);
        return product;
    }
}
