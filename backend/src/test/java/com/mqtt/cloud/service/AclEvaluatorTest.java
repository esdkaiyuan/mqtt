package com.mqtt.cloud.service;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.service.impl.DeviceSecretServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AclEvaluatorTest {

    private AccessControlProperties accessControlProperties;
    private RealtimeProperties realtimeProperties;
    private AclEvaluator evaluator;

    @BeforeEach
    void setUp() {
        accessControlProperties = new AccessControlProperties();
        accessControlProperties.setEnforceAuth(true);
        realtimeProperties = new RealtimeProperties();
        evaluator = new AclEvaluator(accessControlProperties, realtimeProperties, new DeviceSecretServiceImpl());
    }

    @Test
    void device_can_publish_own_namespace_only() {
        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "device/sensor-01/data")).isTrue();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "device/sensor-02/data")).isFalse();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "device/sensor-01/cmd/reboot")).isFalse();
    }

    @Test
    void device_can_subscribe_own_cmd_only() {
        assertThat(evaluator.allow("esp32-fall.sensor-01", "subscribe", "device/sensor-01/cmd/#")).isTrue();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "subscribe", "device/sensor-01/data")).isFalse();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "subscribe", "device/+/data")).isFalse();
    }

    @Test
    void platform_account_can_subscribe_all_and_publish_cmd() {
        assertThat(evaluator.allow("PLATFORM", "subscribe", "device/+/#")).isTrue();
        assertThat(evaluator.allow("PLATFORM", "publish", "device/sensor-01/cmd/reboot")).isTrue();
        assertThat(evaluator.allow("PLATFORM", "publish", "device/sensor-01/data")).isFalse();
    }

    @Test
    void frontend_readonly_account_allowed_only_when_enabled() {
        assertThat(evaluator.allow("FRONTEND_READONLY", "subscribe", "device/+/data")).isFalse();

        realtimeProperties.setDirectFrontendEnabled(true);
        assertThat(evaluator.allow("FRONTEND_READONLY", "subscribe", "device/+/data")).isTrue();
        assertThat(evaluator.allow("FRONTEND_READONLY", "publish", "device/sensor-01/data")).isFalse();
    }

    @Test
    void allow_all_when_enforcement_disabled() {
        accessControlProperties.setEnforceAuth(false);

        assertThat(evaluator.allow("whatever", "publish", "anything")).isTrue();
    }

    @Test
    void malformed_username_is_denied() {
        assertThat(evaluator.allow("bad-username", "publish", "device/x/data")).isFalse();
    }
}