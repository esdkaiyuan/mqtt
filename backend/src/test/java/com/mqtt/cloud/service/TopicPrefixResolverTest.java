package com.mqtt.cloud.service;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 主题前缀模板解析：默认模板必须与既有硬编码前缀 {@code device/{deviceKey}/} 逐字等价，
 * 否则上线即造成存量设备主题归属判定回归。
 */
class TopicPrefixResolverTest {

    private final TopicPrefixResolver resolver = new TopicPrefixResolver();

    @Test
    void default_template_equals_legacy_prefix() {
        assertThat(resolver.resolve("device/{deviceKey}", "esp32-fall", "sensor-01"))
                .isEqualTo("device/sensor-01/");
    }

    @Test
    void null_and_blank_template_fall_back_to_default() {
        assertThat(resolver.resolve(null, "esp32-fall", "sensor-01")).isEqualTo("device/sensor-01/");
        assertThat(resolver.resolve("   ", "esp32-fall", "sensor-01")).isEqualTo("device/sensor-01/");
    }

    @Test
    void both_placeholders_are_substituted() {
        assertThat(resolver.resolve("{productKey}/{deviceKey}", "esp32-fall", "sensor-01"))
                .isEqualTo("esp32-fall/sensor-01/");
        assertThat(resolver.resolve("iot/{productKey}/{deviceKey}", "esp32-fall", "sensor-01"))
                .isEqualTo("iot/esp32-fall/sensor-01/");
    }

    @Test
    void trailing_slash_is_normalized_to_single() {
        assertThat(resolver.resolve("device/{deviceKey}///", "esp32-fall", "sensor-01"))
                .isEqualTo("device/sensor-01/");
    }

    @Test
    void subscription_template_replaces_device_with_wildcard() {
        assertThat(resolver.resolveForSubscription("device/{deviceKey}", "esp32-fall"))
                .isEqualTo("device/+/");
        assertThat(resolver.resolveForSubscription("{productKey}/{deviceKey}", "esp32-fall"))
                .isEqualTo("esp32-fall/+/");
    }

    @Test
    void unknown_placeholder_is_rejected() {
        assertThatThrownBy(() -> resolver.resolve("device/{unknown}", "esp32-fall", "sensor-01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID));
    }

    @Test
    void unclosed_placeholder_is_rejected() {
        assertThatThrownBy(() -> resolver.resolve("device/{deviceKey", "esp32-fall", "sensor-01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID));
    }

    @Test
    void stray_closing_brace_is_rejected() {
        assertThatThrownBy(() -> resolver.resolve("device/{deviceKey}}", "esp32-fall", "sensor-01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID));
    }

    /** 模板本体不得含 MQTT 通配符，否则订阅侧会生成越权过滤器。 */
    @Test
    void mqtt_wildcards_in_template_are_rejected() {
        assertThatThrownBy(() -> resolver.resolve("device/+/data", "esp32-fall", "sensor-01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID));
        assertThatThrownBy(() -> resolver.resolve("device/{deviceKey}/#", "esp32-fall", "sensor-01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID));
    }

    @Test
    void overlong_template_is_rejected() {
        String longTemplate = "device/" + "a".repeat(130) + "/{deviceKey}";

        assertThatThrownBy(() -> resolver.resolve(longTemplate, "esp32-fall", "sensor-01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID));
    }

    @Test
    void target_device_is_resolved_for_single_segment_template() {
        assertThat(resolver.resolveTarget("device/{deviceKey}", "esp32-fall", "device/sensor-02/data"))
                .isEqualTo(new TopicPrefixResolver.TargetDevice("sensor-02", "data"));
    }

    /**
     * 设备键段位由模板中 {@code {deviceKey}} 的位置决定，多段模板不得退化为固定下标。
     */
    @Test
    void target_device_position_follows_template_placeholder() {
        assertThat(resolver.resolveTarget("{productKey}/{deviceKey}", "esp32-fall", "esp32-fall/sensor-02/data"))
                .isEqualTo(new TopicPrefixResolver.TargetDevice("sensor-02", "data"));
        assertThat(resolver.resolveTarget("iot/{productKey}/{deviceKey}", "esp32-fall",
                "iot/esp32-fall/sensor-02/data"))
                .isEqualTo(new TopicPrefixResolver.TargetDevice("sensor-02", "data"));
        assertThat(resolver.resolveTarget("iot/{productKey}/v2/{deviceKey}", "esp32-fall",
                "iot/esp32-fall/v2/sensor-02/data"))
                .isEqualTo(new TopicPrefixResolver.TargetDevice("sensor-02", "data"));
    }

    @Test
    void target_device_keeps_multi_segment_message_type() {
        assertThat(resolver.resolveTarget("device/{deviceKey}", "esp32-fall", "device/sensor-02/cmd/down"))
                .isEqualTo(new TopicPrefixResolver.TargetDevice("sensor-02", "cmd/down"));
    }

    /** 主题不在模板形状内（首段不符、产品段不符）时返回 null，交由调用方拒绝。 */
    @Test
    void target_device_resolution_rejects_topic_outside_template_shape() {
        assertThat(resolver.resolveTarget("device/{deviceKey}", "esp32-fall", "other/sensor-02/data")).isNull();
        assertThat(resolver.resolveTarget("device/{deviceKey}", "esp32-fall", "devices/sensor-02/data")).isNull();
        assertThat(resolver.resolveTarget("iot/{productKey}/{deviceKey}", "esp32-fall",
                "iot/esp32-other/sensor-02/data")).isNull();
    }

    /** 占位符之后没有消息类型时不构成可判定的消息主题，避免退化为无消息类型的空串放行。 */
    @Test
    void target_device_resolution_rejects_topic_without_message_type() {
        assertThat(resolver.resolveTarget("device/{deviceKey}", "esp32-fall", "device/sensor-02")).isNull();
        assertThat(resolver.resolveTarget("device/{deviceKey}", "esp32-fall", "device/sensor-02/")).isNull();
    }

    @Test
    void target_device_resolution_rejects_invalid_template() {
        assertThatThrownBy(() -> resolver.resolveTarget("device/{unknown}", "esp32-fall", "device/sensor-02/data"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID));
    }
}
