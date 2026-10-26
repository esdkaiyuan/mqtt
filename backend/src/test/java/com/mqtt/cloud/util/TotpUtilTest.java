package com.mqtt.cloud.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TotpUtil} 单元测试（T-58）。
 */
class TotpUtilTest {

    /** RFC 6238 测试密钥：ASCII "12345678901234567890" 的 Base32。 */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void base32_roundtrip() {
        byte[] data = "12345678901234567890".getBytes();
        String encoded = TotpUtil.base32Encode(data);

        assertThat(encoded).isEqualTo(RFC_SECRET);
        assertThat(TotpUtil.base32Decode(encoded)).isEqualTo(data);
    }

    @Test
    void code_at_matches_rfc6238_vectors() {
        assertThat(TotpUtil.codeAt(RFC_SECRET, 0L, 6)).isEqualTo("755224");
        assertThat(TotpUtil.codeAt(RFC_SECRET, 1L, 6)).isEqualTo("287082");
        assertThat(TotpUtil.codeAt(RFC_SECRET, 2L, 6)).isEqualTo("359152");
    }

    @Test
    void verify_accepts_current_step_and_tolerates_window() {
        long step = 30L;
        long counter = System.currentTimeMillis() / 1000L / step;
        String code = TotpUtil.codeAt(RFC_SECRET, counter, 6);

        assertThat(TotpUtil.verify(RFC_SECRET, code, 30, 1, 6)).isTrue();
        assertThat(TotpUtil.verify(RFC_SECRET, code, 30, 0, 6)).isTrue();
    }

    @Test
    void verify_rejects_wrong_code_or_bad_input() {
        long farCounter = System.currentTimeMillis() / 1000L / 30L + 1000L;
        String wrong = TotpUtil.codeAt(RFC_SECRET, farCounter, 6);

        assertThat(TotpUtil.verify(RFC_SECRET, wrong, 30, 1, 6)).isFalse();
        assertThat(TotpUtil.verify(RFC_SECRET, "12345", 30, 1, 6)).isFalse();
        assertThat(TotpUtil.verify(null, "123456", 30, 1, 6)).isFalse();
        assertThat(TotpUtil.verify(RFC_SECRET, "12345a", 30, 1, 6)).isFalse();
    }

    @Test
    void generated_secret_is_base32_and_url_contains_it() {
        String secret = TotpUtil.generateSecret(20);

        assertThat(secret).hasSize(32);
        assertThat(secret).matches("[A-Z2-7]+");
        assertThat(TotpUtil.base32Decode(secret)).hasSize(20);

        String uri = TotpUtil.otpauthUri("MQTT云平台", "admin", secret, 6, 30);
        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=" + secret);
        assertThat(uri).contains("issuer=MQTT");
        assertThat(uri).contains("period=30");
    }
}