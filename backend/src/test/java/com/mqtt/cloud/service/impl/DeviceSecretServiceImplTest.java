package com.mqtt.cloud.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceSecretServiceImplTest {

    private final DeviceSecretServiceImpl service = new DeviceSecretServiceImpl();

    @Test
    void generateSecret_should_be_32_chars_and_unique() {
        String a = service.generateSecret();
        String b = service.generateSecret();

        assertThat(a).hasSize(32).matches("[A-Za-z0-9]+");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hash_and_matches_should_round_trip() {
        String secret = service.generateSecret();
        String hash = service.hash(secret);

        assertThat(hash).startsWith("$2");
        assertThat(service.matches(secret, hash)).isTrue();
        assertThat(service.matches("wrong-secret", hash)).isFalse();
        assertThat(service.matches(secret, null)).isFalse();
    }

    @Test
    void buildAndParseUsername_should_round_trip() {
        String username = service.buildUsername("esp32-fall", "sensor-01");

        assertThat(username).isEqualTo("esp32-fall.sensor-01");
        assertThat(service.parseUsername(username)).containsExactly("esp32-fall", "sensor-01");
        assertThat(service.parseUsername("no-dot")).isNull();
        assertThat(service.parseUsername("a.b.c")).isNull();
    }

    @Test
    void isPlatformUsername_should_match_exact_case_insensitive() {
        assertThat(service.isPlatformUsername("PLATFORM")).isTrue();
        assertThat(service.isPlatformUsername("platform")).isTrue();
        assertThat(service.isPlatformUsername("PLATFORM.x")).isFalse();
    }
}