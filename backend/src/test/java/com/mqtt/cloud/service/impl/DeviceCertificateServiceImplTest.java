package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.AuthModeValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.AuthExtensionProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCertificate;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceCertificateMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCertificateService;
import com.mqtt.cloud.service.DeviceCertificateService.IssuedCertificate;
import com.mqtt.cloud.service.DeviceCertificateService.RevokedCertificate;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.ProductService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备证书服务单测（T-25）。
 * <p>
 * CA 材料在内存中生成后落临时目录，由实现类按配置路径读取，
 * 覆盖签发、重复签发、吊销与吊销态查询四条路径。
 */
@ExtendWith(MockitoExtension.class)
class DeviceCertificateServiceImplTest {

    private static final String PRODUCT_KEY = "esp32-fall";
    private static final String DEVICE_KEY = "sensor-01";
    private static final String EXPECTED_SUBJECT = "CN=esp32-fall.sensor-01,O=MQTT Cloud";

    /** 内存 CA：测试期只生成一次，签发链路的核验以它的公钥为准 */
    private static X509Certificate caCertificate;
    private static Path caCertPath;
    private static Path caKeyPath;

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private ProductService productService;
    @Mock
    private DeviceCertificateMapper deviceCertificateMapper;
    @Mock
    private DeviceAuthCacheService authCacheService;

    private final DeviceSecretService deviceSecretService = new DeviceSecretServiceImpl();

    private AuthExtensionProperties properties;
    private DeviceCertificateServiceImpl service;

    @BeforeAll
    static void generateCa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair caKeyPair = generator.generateKeyPair();

        X500Name caName = new X500Name("CN=MQTT Cloud Test CA,O=MQTT Cloud");
        LocalDateTime now = LocalDateTime.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caName,
                BigInteger.valueOf(System.currentTimeMillis()),
                toDate(now.minusDays(1)),
                toDate(now.plusDays(3650)),
                caName,
                caKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        caCertificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate())));

        Path dir = Files.createTempDirectory("t25-ca-");
        caCertPath = dir.resolve("ca.crt");
        caKeyPath = dir.resolve("ca.key");
        Files.writeString(caCertPath, toPem("CERTIFICATE", caCertificate.getEncoded()), StandardCharsets.UTF_8);
        // RSA 私钥的 getEncoded() 为 PKCS#8 DER，顺带覆盖实现类的 PKCS#8 读取分支
        Files.writeString(caKeyPath, toPem("PRIVATE KEY", caKeyPair.getPrivate().getEncoded()), StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        properties = new AuthExtensionProperties();
        properties.getX509().setEnabled(true);
        properties.getX509().setCaCertPath(caCertPath.toString());
        properties.getX509().setCaKeyPath(caKeyPath.toString());
        service = new DeviceCertificateServiceImpl(deviceMapper, productService, deviceCertificateMapper,
                authCacheService, deviceSecretService, properties);
    }

    @Test
    void issue_should_sign_certificate_bound_to_device_and_persist() throws Exception {
        properties.getX509().setValidityDays(30);
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.X509));

        IssuedCertificate issued = service.issue(1L);

        assertThat(issued.deviceId()).isEqualTo(1L);
        assertThat(issued.subject()).isEqualTo(EXPECTED_SUBJECT);
        assertThat(issued.serial()).hasSize(16).matches("[0-9A-F]{16}");
        assertThat(ChronoUnit.DAYS.between(issued.notBefore(), issued.notAfter())).isEqualTo(30);
        assertThat(issued.certificatePem())
                .startsWith("-----BEGIN CERTIFICATE-----")
                .endsWith("-----END CERTIFICATE-----\n");
        assertThat(issued.privateKeyPem())
                .startsWith("-----BEGIN PRIVATE KEY-----")
                .endsWith("-----END PRIVATE KEY-----\n");

        // 返回的 PEM 必须能反解，且确由 CA 签发、主体绑定产品与设备
        X509Certificate parsed = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(
                        issued.certificatePem().getBytes(StandardCharsets.UTF_8)));
        parsed.verify(caCertificate.getPublicKey());
        assertThat(parsed.getIssuerX500Principal().getName()).contains("CN=MQTT Cloud Test CA");
        assertThat(parsed.getSubjectX500Principal().getName()).contains("CN=esp32-fall.sensor-01");
        assertThat(parsed.getSerialNumber().toString(16).toUpperCase())
                .isEqualTo(issued.serial().replaceFirst("^0+(?=.)", ""));

        ArgumentCaptor<DeviceCertificate> captor = ArgumentCaptor.forClass(DeviceCertificate.class);
        verify(deviceCertificateMapper).insert(captor.capture());
        DeviceCertificate row = captor.getValue();
        assertThat(row.getDeviceId()).isEqualTo(1L);
        assertThat(row.getProductKey()).isEqualTo(PRODUCT_KEY);
        assertThat(row.getDeviceKey()).isEqualTo(DEVICE_KEY);
        assertThat(row.getSerial()).isEqualTo(issued.serial());
        assertThat(row.getSubject()).isEqualTo(EXPECTED_SUBJECT);
        assertThat(row.getIssuedAt()).isEqualTo(issued.notBefore());
        assertThat(row.getNotAfter()).isEqualTo(issued.notAfter());
        assertThat(row.getRevokedAt()).isNull();
        // 私钥只随响应返回，任何字段都不落库
        assertThat(row.getRevokeReason()).isNull();
    }

    @Test
    void issue_twice_should_insert_two_rows_and_leave_old_certificate_active() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.X509));

        IssuedCertificate first = service.issue(1L);
        IssuedCertificate second = service.issue(1L);

        assertThat(second.serial()).isNotEqualTo(first.serial());
        ArgumentCaptor<DeviceCertificate> captor = ArgumentCaptor.forClass(DeviceCertificate.class);
        verify(deviceCertificateMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        // 重复签发表明重新签发，旧证书不自动吊销，留待显式吊销
        assertThat(captor.getAllValues()).allSatisfy(row -> assertThat(row.getRevokedAt()).isNull());
    }

    @Test
    void issue_should_reject_when_product_auth_mode_not_x509() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.SECRET));

        BusinessException error = catchThrowableOfType(() -> service.issue(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.AUTH_MODE_UNSUPPORTED);
    }

    @Test
    void issue_should_reject_when_x509_switch_disabled() {
        properties.getX509().setEnabled(false);
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));
        when(productService.requireById(10L)).thenReturn(product(PRODUCT_KEY, AuthModeValue.X509));

        BusinessException error = catchThrowableOfType(() -> service.issue(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.AUTH_MODE_UNSUPPORTED);
    }

    @Test
    void issue_should_reject_missing_device() {
        when(deviceMapper.selectById(404L)).thenReturn(null);

        BusinessException error = catchThrowableOfType(() -> service.issue(404L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.DEVICE_NOT_FOUND);
    }

    @Test
    void issue_should_reject_soft_deleted_device() {
        Device deleted = device(1L, DEVICE_KEY, 10L);
        deleted.setDeleted(1);
        when(deviceMapper.selectById(1L)).thenReturn(deleted);

        BusinessException error = catchThrowableOfType(() -> service.issue(1L), BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.DEVICE_NOT_FOUND);
    }

    @Test
    void revoke_should_stamp_revoked_fields_and_evict_cache() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));
        DeviceCertificate active = certificate(9L, "A1B2C3D4E5F60718");
        when(deviceCertificateMapper.selectActiveByDeviceId(1L)).thenReturn(active);

        RevokedCertificate revoked = service.revoke(1L, "  device_decommissioned  ");

        assertThat(revoked.deviceId()).isEqualTo(1L);
        assertThat(revoked.serial()).isEqualTo("A1B2C3D4E5F60718");
        assertThat(revoked.reason()).isEqualTo("device_decommissioned");
        assertThat(revoked.revokedAt()).isNotNull();

        ArgumentCaptor<DeviceCertificate> captor = ArgumentCaptor.forClass(DeviceCertificate.class);
        verify(deviceCertificateMapper).updateById(captor.capture());
        DeviceCertificate updated = captor.getValue();
        assertThat(updated.getRevokedAt()).isEqualTo(revoked.revokedAt());
        assertThat(updated.getRevokeReason()).isEqualTo("device_decommissioned");
        // 吊销后必须立即清缓存，否则旧证书在 TTL 内仍可放行
        verify(authCacheService).evict(PRODUCT_KEY, DEVICE_KEY);
    }

    @Test
    void revoke_should_accept_blank_reason_as_null() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));
        when(deviceCertificateMapper.selectActiveByDeviceId(1L)).thenReturn(certificate(9L, "A1B2C3D4E5F60718"));

        RevokedCertificate revoked = service.revoke(1L, "   ");

        assertThat(revoked.reason()).isNull();
        ArgumentCaptor<DeviceCertificate> captor = ArgumentCaptor.forClass(DeviceCertificate.class);
        verify(deviceCertificateMapper).updateById(captor.capture());
        assertThat(captor.getValue().getRevokeReason()).isNull();
    }

    @Test
    void revoke_should_reject_when_no_active_certificate() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));
        when(deviceCertificateMapper.selectActiveByDeviceId(1L)).thenReturn(null);

        BusinessException error = catchThrowableOfType(() -> service.revoke(1L, "decommissioned"),
                BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.DEVICE_CERTIFICATE_REVOKE_FAILED);
    }

    @Test
    void revoke_should_reject_reason_longer_than_64() {
        when(deviceMapper.selectById(1L)).thenReturn(device(1L, DEVICE_KEY, 10L));

        BusinessException error = catchThrowableOfType(() -> service.revoke(1L, "x".repeat(65)),
                BusinessException.class);

        assertThat(error.getResultCode()).isEqualTo(ResultCode.BAD_REQUEST);
    }

    @Test
    void isRevoked_should_return_true_without_active_certificate() {
        when(deviceCertificateMapper.selectActiveByDeviceId(1L)).thenReturn(null);

        assertThat(service.isRevoked(1L)).isTrue();
    }

    @Test
    void isRevoked_should_return_true_when_certificate_expired() {
        DeviceCertificate expired = certificate(9L, "A1B2C3D4E5F60718");
        expired.setNotAfter(LocalDateTime.now().minusMinutes(1));
        when(deviceCertificateMapper.selectActiveByDeviceId(1L)).thenReturn(expired);

        assertThat(service.isRevoked(1L)).isTrue();
    }

    @Test
    void isRevoked_should_return_false_when_certificate_active() {
        DeviceCertificate active = certificate(9L, "A1B2C3D4E5F60718");
        active.setNotAfter(LocalDateTime.now().plusDays(30));
        when(deviceCertificateMapper.selectActiveByDeviceId(1L)).thenReturn(active);

        assertThat(service.isRevoked(1L)).isFalse();
    }

    @Test
    void isRevoked_should_return_true_for_null_device_id() {
        assertThat(service.isRevoked(null)).isTrue();
        // 设备都取不到时不应触库
        verify(deviceCertificateMapper, never()).selectActiveByDeviceId(any());
    }

    private static Device device(Long id, String deviceKey, Long productId) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey(deviceKey);
        device.setProductId(productId);
        device.setDeleted(0);
        return device;
    }

    private static Product product(String productKey, String authMode) {
        Product product = new Product();
        product.setId(10L);
        product.setProductKey(productKey);
        product.setAuthMode(authMode);
        return product;
    }

    private static DeviceCertificate certificate(Long id, String serial) {
        DeviceCertificate row = new DeviceCertificate();
        row.setId(id);
        row.setDeviceId(1L);
        row.setProductKey(PRODUCT_KEY);
        row.setDeviceKey(DEVICE_KEY);
        row.setSerial(serial);
        row.setSubject(EXPECTED_SUBJECT);
        row.setIssuedAt(LocalDateTime.now().minusDays(1));
        row.setNotAfter(LocalDateTime.now().plusDays(365));
        return row;
    }

    private static Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static String toPem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
