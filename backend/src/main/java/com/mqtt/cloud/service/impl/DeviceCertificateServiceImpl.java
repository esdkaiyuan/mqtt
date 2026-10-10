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
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;

/**
 * 设备证书服务实现（T-25）。
 * <p>
 * 签发链路以配置的 CA 证书与私钥为由设备生成的一次性密钥对签证书，
 * 主体固定为 {@code CN={productKey}.{deviceKey},O=MQTT Cloud}，
 * 与 EMQX 侧 {@code peer_cert_as_username=cn} 提取的用户名一致。
 * 私钥只随本次响应返回，不入库、不落日志；库中只留 {@code serial} / {@code subject} / {@code not_after}，
 * 供认证回调判定吊销与过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCertificateServiceImpl implements DeviceCertificateService {

    /** 证书主体中的组织名，与设计文档 §8.2 一致 */
    private static final String SUBJECT_ORGANIZATION = "MQTT Cloud";
    /** 设备密钥算法与长度：RSA 2048 与 EMQX mTLS 兼容面最广 */
    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    /** 签名算法：随 CA 私钥类型固定为 SHA256withRSA */
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    /** 序列号字节数：8 字节随机数渲染为 16 位十六进制，与既有展示形态一致 */
    private static final int SERIAL_BYTES = 8;
    /** 吊销原因上限，与 `revoke_reason VARCHAR(64)` 对齐 */
    private static final int REASON_MAX_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeviceMapper deviceMapper;
    private final ProductService productService;
    private final DeviceCertificateMapper deviceCertificateMapper;
    private final DeviceAuthCacheService authCacheService;
    private final DeviceSecretService deviceSecretService;
    private final AuthExtensionProperties authExtensionProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IssuedCertificate issue(Long deviceId) {
        Device device = requireDevice(deviceId);
        Product product = productService.requireById(device.getProductId());
        AuthExtensionProperties.X509 x509 = authExtensionProperties.getX509();
        if (!x509.isEnabled() || !AuthModeValue.X509.equals(product.getAuthMode())) {
            // 开关未开与产品未选 X.509 同属「该模式不可签发」，不区分提示以免暴露部署配置
            throw new BusinessException(ResultCode.AUTH_MODE_UNSUPPORTED);
        }
        try {
            return signAndPersist(device, product, x509);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("设备证书签发失败，deviceId={}", deviceId, e);
            throw new BusinessException(ResultCode.DEVICE_CERTIFICATE_ISSUE_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RevokedCertificate revoke(Long deviceId, String reason) {
        requireDevice(deviceId);
        String normalizedReason = normalizeReason(reason);
        DeviceCertificate active = deviceCertificateMapper.selectActiveByDeviceId(deviceId);
        if (active == null) {
            throw new BusinessException(ResultCode.DEVICE_CERTIFICATE_REVOKE_FAILED);
        }
        LocalDateTime revokedAt = LocalDateTime.now();
        active.setRevokedAt(revokedAt);
        active.setRevokeReason(normalizedReason);
        deviceCertificateMapper.updateById(active);
        // 清缓存后下一次认证回调即读到吊销态，与设备重连之间为最短越权窗口
        authCacheService.evict(active.getProductKey(), active.getDeviceKey());
        log.info("设备证书已吊销，deviceId={}，serial={}", deviceId, active.getSerial());
        return new RevokedCertificate(deviceId, active.getSerial(), revokedAt, normalizedReason);
    }

    @Override
    public boolean isRevoked(Long deviceId) {
        if (deviceId == null) {
            return true;
        }
        DeviceCertificate active = deviceCertificateMapper.selectActiveByDeviceId(deviceId);
        if (active == null || active.getNotAfter() == null) {
            return true;
        }
        return !active.getNotAfter().isAfter(LocalDateTime.now());
    }

    /**
     * 以 CA 材料签发证书并落库，返回一次性明文私钥。
     * <p>
     * 序列号取 8 字节随机数，避免同设备重签时序列号撞车触发唯一约束。
     */
    private IssuedCertificate signAndPersist(Device device, Product product,
                                             AuthExtensionProperties.X509 x509) throws Exception {
        String commonName = deviceSecretService.buildUsername(product.getProductKey(), device.getDeviceKey());
        String subjectDn = "CN=" + commonName + ",O=" + SUBJECT_ORGANIZATION;

        X509Certificate caCertificate = readCaCertificate(x509.getCaCertPath());
        PrivateKey caPrivateKey = readCaPrivateKey(x509.getCaKeyPath());

        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        generator.initialize(KEY_SIZE);
        KeyPair keyPair = generator.generateKeyPair();

        LocalDateTime notBefore = LocalDateTime.now();
        LocalDateTime notAfter = notBefore.plusDays(x509.getValidityDays());
        BigInteger serial = new BigInteger(SERIAL_BYTES * 8, RANDOM);
        String serialHex = String.format("%0" + (SERIAL_BYTES * 2) + "X", serial);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caCertificate, serial, toDate(notBefore), toDate(notAfter),
                new X500Name(subjectDn), keyPair.getPublic());
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(caPrivateKey)));

        persist(device, product, serialHex, subjectDn, notBefore, notAfter);

        return new IssuedCertificate(device.getId(), subjectDn, serialHex,
                notBefore, notAfter,
                toPem("CERTIFICATE", certificate.getEncoded()),
                toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
    }

    private void persist(Device device, Product product, String serialHex, String subjectDn,
                         LocalDateTime notBefore, LocalDateTime notAfter) {
        DeviceCertificate row = new DeviceCertificate();
        row.setDeviceId(device.getId());
        row.setProductKey(product.getProductKey());
        row.setDeviceKey(device.getDeviceKey());
        row.setSerial(serialHex);
        row.setSubject(subjectDn);
        row.setIssuedAt(notBefore);
        row.setNotAfter(notAfter);
        deviceCertificateMapper.insert(row);
    }

    private Device requireDevice(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || Integer.valueOf(1).equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > REASON_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "吊销原因长度不得超过 " + REASON_MAX_LENGTH + " 字符");
        }
        return trimmed;
    }

    private X509Certificate readCaCertificate(String path) throws Exception {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    /**
     * 读取 CA 私钥，兼容 PKCS#8 与传统的 RSA/EC PEM 两种封装。
     */
    private PrivateKey readCaPrivateKey(String path) throws Exception {
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8))) {
            Object parsed = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair).getPrivate();
            }
            if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            throw new IllegalArgumentException("CA 私钥文件不含可识别的 PEM 私钥：" + path);
        }
    }

    private static Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** 按 RFC 7468 手动拼装 PEM：每行 64 字符，避免额外依赖 PEM 写出工具。 */
    private static String toPem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
