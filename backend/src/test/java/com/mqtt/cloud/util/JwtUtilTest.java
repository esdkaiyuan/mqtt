package com.mqtt.cloud.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    @Test
    void 空白密钥必须启动失败() {
        assertThatThrownBy(() -> new JwtUtil("", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void 过短密钥必须启动失败() {
        assertThatThrownBy(() -> new JwtUtil("short-secret", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void 合法密钥可签发并解析令牌() {
        JwtUtil jwt = new JwtUtil("test-only-jwt-secret-which-is-long-enough-123", 60_000);
        String token = jwt.generateToken(7L, "admin", "ADMIN");

        Claims claims = jwt.parse(token);

        assertThat(jwt.getUserId(claims)).isEqualTo(7L);
        assertThat(jwt.getUsername(claims)).isEqualTo("admin");
        assertThat(jwt.getRole(claims)).isEqualTo("ADMIN");
        assertThat(jwt.getExpirationSeconds()).isEqualTo(60);
    }
}
