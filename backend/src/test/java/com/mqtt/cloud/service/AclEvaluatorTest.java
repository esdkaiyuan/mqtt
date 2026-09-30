package com.mqtt.cloud.service;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.service.impl.DeviceSecretServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclEvaluatorTest {

    @Mock
    private DeviceAccessGuard deviceAccessGuard;

    private AccessControlProperties accessControlProperties;
    private RealtimeProperties realtimeProperties;
    private AclEvaluator evaluator;

    @BeforeEach
    void setUp() {
        accessControlProperties = new AccessControlProperties();
        accessControlProperties.setEnforceAuth(true);
        realtimeProperties = new RealtimeProperties();
        evaluator = new AclEvaluator(accessControlProperties, realtimeProperties,
                new DeviceSecretServiceImpl(), deviceAccessGuard);
        // 默认放行准入判定，使各用例聚焦主题前缀规则；禁用/停用语义由专门用例覆盖
        lenient().when(deviceAccessGuard.resolve(anyString(), anyString()))
                .thenReturn(new DeviceAuthCacheService.AuthMeta(10L, "ENABLED", 1, "hash"));
        lenient().when(deviceAccessGuard.isPermitted(any())).thenReturn(true);
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

    /**
     * 禁用/停用后，已连接会话在自有主题上的收发同样被拒 —— 这是「禁用只作用于连接」缺口的收敛点。
     */
    @Test
    void device_denied_when_guard_rejects_even_for_own_topic() {
        when(deviceAccessGuard.isPermitted(any())).thenReturn(false);

        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "device/sensor-01/data")).isFalse();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "subscribe", "device/sensor-01/cmd/#")).isFalse();
    }

    /** 平台账号与前端只读账号不是设备，不应被产品/设备准入判定牵连。 */
    @Test
    void platform_and_frontend_accounts_bypass_device_guard() {
        assertThat(evaluator.allow("PLATFORM", "subscribe", "device/+/#")).isTrue();
        realtimeProperties.setDirectFrontendEnabled(true);
        assertThat(evaluator.allow("FRONTEND_READONLY", "subscribe", "device/+/data")).isTrue();

        verifyNoInteractions(deviceAccessGuard);
    }
}