package com.mqtt.cloud.service;

import com.mqtt.cloud.common.constant.DeviceNodeType;
import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.impl.DeviceSecretServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclEvaluatorTest {

    /** 委托用例共用的拓扑快照标识：网关 1L 挂载子设备 2L。 */
    private static final long GATEWAY_DEVICE_ID = 1L;
    private static final long SUB_DEVICE_ID = 2L;

    @Mock
    private DeviceAccessGuard deviceAccessGuard;

    @Mock
    private ProductService productService;

    private AccessControlProperties accessControlProperties;
    private RealtimeProperties realtimeProperties;
    private AclEvaluator evaluator;

    @BeforeEach
    void setUp() {
        accessControlProperties = new AccessControlProperties();
        accessControlProperties.setEnforceAuth(true);
        realtimeProperties = new RealtimeProperties();
        evaluator = new AclEvaluator(accessControlProperties, realtimeProperties,
                new DeviceSecretServiceImpl(), deviceAccessGuard, new TopicPrefixResolver(),
                productService);
        // 默认放行准入判定，使各用例聚焦主题前缀规则；禁用/停用语义由专门用例覆盖。
        // 主题前缀给默认模板 device/{deviceKey}，等价迁移前硬编码的 device/{deviceKey}/ 前缀。
        lenient().when(deviceAccessGuard.resolve(anyString(), anyString()))
                .thenReturn(new DeviceAuthCacheService.AuthMeta(
                        10L, 1L, "ENABLED", 1, "SECRET", "hash", "device/{deviceKey}", "DIRECT", null));
        lenient().when(deviceAccessGuard.isPermitted(any())).thenReturn(true);
        // 平台分支只认启用产品的模板命名空间，默认给一个默认模板产品，等价迁移前的 device/ 前缀。
        lenient().when(productService.listEnabled()).thenReturn(List.of(product("esp32-fall", "device/{deviceKey}")));
    }

    private static Product product(String productKey, String topicPrefix) {
        Product product = new Product();
        product.setProductKey(productKey);
        product.setTopicPrefix(topicPrefix);
        product.setStatus("ENABLED");
        return product;
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

    /**
     * 产品改自定义模板后，平台的共享订阅前缀随模板平移：平台必须能订阅到新命名空间，
     * 否则 backend 自身的共享订阅会被自家授权拒绝，五类主题一条都收不到（T-25 §14.2 A7）。
     */
    @Test
    void platform_account_follows_custom_topic_template() {
        when(productService.listEnabled())
                .thenReturn(List.of(product("t25-tpl", "factory/{deviceKey}")));

        assertThat(evaluator.allow("PLATFORM", "subscribe", "factory/+/reply")).isTrue();
        assertThat(evaluator.allow("PLATFORM", "subscribe", "factory/+/data")).isTrue();
        assertThat(evaluator.allow("PLATFORM", "publish", "factory/d-01/cmd/reboot")).isTrue();
        assertThat(evaluator.allow("PLATFORM", "publish", "factory/d-01/data")).isFalse();
        // 模板平移后，旧的默认命名空间不再是平台的地盘
        assertThat(evaluator.allow("PLATFORM", "subscribe", "device/+/data")).isFalse();
    }

    /**
     * 停用产品不应再向平台开放其命名空间：允许集合与共享订阅同源于「启用产品」，
     * 产品停用后订阅侧不再建订阅，授权侧也同步收口。
     */
    @Test
    void platform_namespace_narrowed_when_product_disabled() {
        when(productService.listEnabled()).thenReturn(List.of());

        assertThat(evaluator.allow("PLATFORM", "subscribe", "device/+/data")).isFalse();
        assertThat(evaluator.allow("PLATFORM", "publish", "device/sensor-01/cmd/reboot")).isFalse();
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

    /**
     * 产品把主题前缀改成含 {@code {productKey}} 的模板后，设备的归属命名空间随模板整体平移，
     * 默认前缀 {@code device/{deviceKey}/} 不再是该设备的地盘。
     */
    @Test
    void custom_topic_template_relocates_owned_namespace() {
        when(deviceAccessGuard.resolve("esp32-fall", "sensor-01"))
                .thenReturn(new DeviceAuthCacheService.AuthMeta(
                        10L, 1L, "ENABLED", 1, "SECRET", "hash",
                        "iot/{productKey}/{deviceKey}", "DIRECT", null));

        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "iot/esp32-fall/sensor-01/data")).isTrue();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "subscribe", "iot/esp32-fall/sensor-01/cmd/#")).isTrue();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "iot/esp32-fall/sensor-01/cmd/reboot")).isFalse();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "device/sensor-01/data")).isFalse();
    }

    /**
     * 网关代子设备上行：产品模板默认形状下，子设备主题落在网关自有命名空间之外，
     * 由委托分支按「目标设备 parentId 即当前网关」放行。
     */
    @Test
    void gateway_publishes_bound_sub_device_data() {
        stubGatewayWithSubDevice();

        assertThat(evaluator.allow("esp32-fall.gateway-01", "publish", "device/sub-01/data")).isTrue();
    }

    /** 委托链路的例外语义与自有命名空间同源：网关不得代子设备发布下行命令。 */
    @Test
    void gateway_publish_sub_device_cmd_is_denied() {
        stubGatewayWithSubDevice();

        assertThat(evaluator.allow("esp32-fall.gateway-01", "publish", "device/sub-01/cmd/reboot")).isFalse();
    }

    /** 网关代子设备下行：订阅子设备 cmd/ 放行，子设备 data 不得被网关订阅。 */
    @Test
    void gateway_subscribes_bound_sub_device_cmd() {
        stubGatewayWithSubDevice();

        assertThat(evaluator.allow("esp32-fall.gateway-01", "subscribe", "device/sub-01/cmd/#")).isTrue();
        assertThat(evaluator.allow("esp32-fall.gateway-01", "subscribe", "device/sub-01/data")).isFalse();
    }

    /** 目标设备的父网关不是当前设备时不构成委托关系，不得越权代收发。 */
    @Test
    void gateway_denied_for_foreign_sub_device() {
        when(deviceAccessGuard.resolve("esp32-fall", "gateway-01"))
                .thenReturn(meta(GATEWAY_DEVICE_ID, DeviceNodeType.GATEWAY, null));
        when(deviceAccessGuard.resolve("esp32-fall", "sub-99"))
                .thenReturn(meta(3L, DeviceNodeType.SUB, 2L));

        assertThat(evaluator.allow("esp32-fall.gateway-01", "publish", "device/sub-99/data")).isFalse();
        assertThat(evaluator.allow("esp32-fall.gateway-01", "subscribe", "device/sub-99/cmd/#")).isFalse();
    }

    /** 网关被禁用后连自身准入都不过，委托自然一并收口 —— 委托判定不得成为禁用的旁路。 */
    @Test
    void gateway_denied_when_disabled() {
        when(deviceAccessGuard.resolve("esp32-fall", "gateway-01"))
                .thenReturn(meta(GATEWAY_DEVICE_ID, DeviceNodeType.GATEWAY, null));
        when(deviceAccessGuard.isPermitted(any())).thenReturn(false);

        assertThat(evaluator.allow("esp32-fall.gateway-01", "publish", "device/sub-01/data")).isFalse();
    }

    /** 解绑后子设备回落为 DIRECT 且 parent_id 为空，拓扑变更失效两侧缓存后的取值不再构成委托。 */
    @Test
    void gateway_denied_after_sub_device_detached() {
        when(deviceAccessGuard.resolve("esp32-fall", "gateway-01"))
                .thenReturn(meta(GATEWAY_DEVICE_ID, DeviceNodeType.GATEWAY, null));
        when(deviceAccessGuard.resolve("esp32-fall", "sub-01"))
                .thenReturn(meta(SUB_DEVICE_ID, DeviceNodeType.DIRECT, null));

        assertThat(evaluator.allow("esp32-fall.gateway-01", "publish", "device/sub-01/data")).isFalse();
        assertThat(evaluator.allow("esp32-fall.gateway-01", "subscribe", "device/sub-01/cmd/#")).isFalse();
    }

    /** 非网关设备不承担代理职责：即便主题形状合法，DIRECT 角色一律拒绝委托。 */
    @Test
    void direct_device_never_delegates() {
        assertThat(evaluator.allow("esp32-fall.sensor-01", "publish", "device/sub-01/data")).isFalse();
        assertThat(evaluator.allow("esp32-fall.sensor-01", "subscribe", "device/sub-01/cmd/#")).isFalse();
    }

    /**
     * 多段模板下委托仍然成立：目标设备键的段位随模板占位符走，
     * 委托判定不得因模板形状变化而退化为固定下标取段。
     */
    @Test
    void gateway_delegates_under_multi_segment_template() {
        when(deviceAccessGuard.resolve("esp32-fall", "gateway-01"))
                .thenReturn(meta(GATEWAY_DEVICE_ID, DeviceNodeType.GATEWAY, null,
                        "iot/{productKey}/{deviceKey}"));
        when(deviceAccessGuard.resolve("esp32-fall", "sub-01"))
                .thenReturn(meta(SUB_DEVICE_ID, DeviceNodeType.SUB, GATEWAY_DEVICE_ID,
                        "iot/{productKey}/{deviceKey}"));

        assertThat(evaluator.allow("esp32-fall.gateway-01", "publish", "iot/esp32-fall/sub-01/data")).isTrue();
        assertThat(evaluator.allow("esp32-fall.gateway-01", "subscribe", "iot/esp32-fall/sub-01/cmd/#")).isTrue();
        assertThat(evaluator.allow("esp32-fall.gateway-01", "publish", "iot/esp32-fall/sub-01/cmd/reboot")).isFalse();
    }

    /** 子网关（SUB）也可以代理其直接子设备（T-47 多级拓扑）。 */
    @Test
    void sub_gateway_delegates_to_direct_child() {
        when(deviceAccessGuard.resolve("esp32-fall", "sub-gw"))
                .thenReturn(meta(2L, DeviceNodeType.SUB, GATEWAY_DEVICE_ID));
        when(deviceAccessGuard.resolve("esp32-fall", "sub-02"))
                .thenReturn(meta(3L, DeviceNodeType.SUB, 2L));

        assertThat(evaluator.allow("esp32-fall.sub-gw", "publish", "device/sub-02/data")).isTrue();
        assertThat(evaluator.allow("esp32-fall.sub-gw", "subscribe", "device/sub-02/cmd/#")).isTrue();
        assertThat(evaluator.allow("esp32-fall.sub-gw", "publish", "device/sub-02/cmd/reboot")).isFalse();
    }

    /** 网关 1L 与其子设备 2L 的拓扑快照：子设备的 parentId 指向网关 deviceId。 */
    private void stubGatewayWithSubDevice() {
        when(deviceAccessGuard.resolve("esp32-fall", "gateway-01"))
                .thenReturn(meta(GATEWAY_DEVICE_ID, DeviceNodeType.GATEWAY, null));
        when(deviceAccessGuard.resolve("esp32-fall", "sub-01"))
                .thenReturn(meta(SUB_DEVICE_ID, DeviceNodeType.SUB, GATEWAY_DEVICE_ID));
    }

    private static DeviceAuthCacheService.AuthMeta meta(long deviceId, String nodeType, Long parentId) {
        return meta(deviceId, nodeType, parentId, "device/{deviceKey}");
    }

    private static DeviceAuthCacheService.AuthMeta meta(long deviceId, String nodeType, Long parentId,
                                                       String topicPrefix) {
        return new DeviceAuthCacheService.AuthMeta(
                10L, deviceId, "ENABLED", 1, "SECRET", "hash", topicPrefix, nodeType, parentId);
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