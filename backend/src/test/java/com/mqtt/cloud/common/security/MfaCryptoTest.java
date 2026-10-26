package com.mqtt.cloud.common.security;

import com.mqtt.cloud.config.MfaProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MfaCrypto} 单元测试（T-58）。
 */
class MfaCryptoTest {

    private static final String JWT_SECRET = "test-jwt-secret-with-enough-length-32-bytes";

    @Test
    void encrypt_decrypt_roundtrip() {
        MfaCrypto crypto = new MfaCrypto(JWT_SECRET, new MfaProperties());

        String cipher = crypto.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(cipher).isNotEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(crypto.decrypt(cipher)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void decrypt_returns_null_for_tampered_cipher() {
        MfaCrypto crypto = new MfaCrypto(JWT_SECRET, new MfaProperties());
        String cipher = crypto.encrypt("JBSWY3DPEHPK3PXP");
        byte[] payload = Base64.getDecoder().decode(cipher);
        payload[payload.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(payload);

        assertThat(crypto.decrypt(tampered)).isNull();
        assertThat(crypto.decrypt("not-base64!!")).isNull();
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.encrypt(null)).isNull();
    }

    @Test
    void explicit_encryption_key_is_used() {
        MfaProperties properties = new MfaProperties();
        properties.setEncryptionKey("explicit-mfa-encryption-key-32-bytes-long");
        MfaCrypto crypto = new MfaCrypto(JWT_SECRET, properties);

        String cipher = crypto.encrypt("secret");
        assertThat(crypto.decrypt(cipher)).isEqualTo("secret");

        // 换密钥后历史密文无法解密
        MfaProperties other = new MfaProperties();
        other.setEncryptionKey("another-mfa-encryption-key-32-bytes-long!!");
        assertThat(new MfaCrypto(JWT_SECRET, other).decrypt(cipher)).isNull();
    }
}