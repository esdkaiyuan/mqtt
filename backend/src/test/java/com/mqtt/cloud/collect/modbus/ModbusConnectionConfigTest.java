package com.mqtt.cloud.collect.modbus;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Modbus 驱动配置解析单测（T-31 P4）。
 */
class ModbusConnectionConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_use_defaults_when_driver_config_absent() {
        ModbusConnectionConfig config = parse(null, "127.0.0.1", 502, 1, 1000, 2000, 1);

        assertThat(config.host()).isEqualTo("127.0.0.1");
        assertThat(config.port()).isEqualTo(502);
        assertThat(config.unitId()).isEqualTo(1);
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofMillis(1000));
        assertThat(config.readTimeout()).isEqualTo(Duration.ofMillis(2000));
        assertThat(config.retry()).isEqualTo(1);
        assertThat(config.wordOrder()).isEqualTo("ABCD");
    }

    @Test
    void should_override_with_driver_config() {
        String json = "{\"host\":\"192.168.1.10\",\"port\":1502,\"unitId\":7,"
                + "\"connectTimeoutMs\":500,\"readTimeoutMs\":800,\"retry\":3,\"wordOrder\":\"cdab\"}";

        ModbusConnectionConfig config = parse(json, "127.0.0.1", 502, 1, 1000, 2000, 1);

        assertThat(config.host()).isEqualTo("192.168.1.10");
        assertThat(config.wordOrder()).isEqualTo("CDAB");
        assertThat(config.port()).isEqualTo(1502);
        assertThat(config.unitId()).isEqualTo(7);
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofMillis(500));
        assertThat(config.readTimeout()).isEqualTo(Duration.ofMillis(800));
        assertThat(config.retry()).isEqualTo(3);
    }

    @Test
    void missing_host_should_fail() {
        assertThatThrownBy(() -> parse("{}", "", 502, 1, 1000, 2000, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void invalid_json_should_fail() {
        assertThatThrownBy(() -> parse("{not-json", "127.0.0.1", 502, 1, 1000, 2000, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void unit_id_out_of_range_should_fail() {
        assertThatThrownBy(() -> parse("{\"unitId\":248}", "127.0.0.1", 502, 1, 1000, 2000, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitId");
    }

    @Test
    void retry_out_of_range_should_fail() {
        assertThatThrownBy(() -> parse("{\"retry\":11}", "127.0.0.1", 502, 1, 1000, 2000, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry");
    }

    @Test
    void invalid_word_order_should_fail() {
        assertThatThrownBy(() -> parse("{\"wordOrder\":\"XYZ\"}", "127.0.0.1", 502, 1, 1000, 2000, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wordOrder");
    }

    private ModbusConnectionConfig parse(String json, String host, int port, int unitId,
                                         long connectMs, long readMs, int retry) {
        return ModbusConnectionConfig.parse(json, host, port, unitId,
                Duration.ofMillis(connectMs), Duration.ofMillis(readMs), retry, objectMapper);
    }
}
