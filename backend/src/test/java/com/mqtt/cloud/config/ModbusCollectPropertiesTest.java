package com.mqtt.cloud.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modbus 采集配置单测（T-31 P4）：验证采集周期下限钳制。
 */
class ModbusCollectPropertiesTest {

    @Test
    void null_interval_should_fall_back_to_60s() {
        ModbusCollectProperties properties = new ModbusCollectProperties();

        assertThat(properties.effectiveIntervalMs(null)).isEqualTo(60_000);
    }

    @Test
    void interval_below_minimum_should_be_clamped() {
        ModbusCollectProperties properties = new ModbusCollectProperties();
        properties.setMinIntervalMs(1000);

        assertThat(properties.effectiveIntervalMs(100)).isEqualTo(1000);
        assertThat(properties.effectiveIntervalMs(999)).isEqualTo(1000);
    }

    @Test
    void interval_above_minimum_should_stay() {
        ModbusCollectProperties properties = new ModbusCollectProperties();
        properties.setMinIntervalMs(1000);

        assertThat(properties.effectiveIntervalMs(5000)).isEqualTo(5000);
    }
}
