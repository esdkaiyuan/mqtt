package com.mqtt.cloud.util;

import com.mqtt.cloud.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PayloadCodecValidator} 单元测试（T-55）。
 */
class PayloadCodecValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_normalize_defaults_and_drop_unknown_fields() {
        String normalized = PayloadCodecValidator.normalize("""
                {"extra":1,"template":{"method":"thing.event.property.post","params":{}}}
                """, mapper);

        assertThat(normalized).contains("\"version\":1", "\"mode\":\"TEMPLATE\"",
                "\"fallbackToRaw\":true", "\"template\"");
        assertThat(normalized).doesNotContain("extra");
    }

    @Test
    void should_return_null_for_blank() {
        assertThat(PayloadCodecValidator.normalize(null, mapper)).isNull();
        assertThat(PayloadCodecValidator.normalize("  ", mapper)).isNull();
    }

    @Test
    void should_reject_invalid_json_or_non_object() {
        assertInvalid("{bad");
        assertInvalid("[1,2]");
        assertInvalid("null");
    }

    @Test
    void should_reject_unsupported_version_or_mode() {
        assertInvalid("{\"version\":2,\"template\":{\"method\":\"m\"}}");
        assertInvalid("{\"mode\":\"SCRIPT\",\"template\":{\"method\":\"m\"}}");
    }

    @Test
    void should_reject_missing_template_or_method() {
        assertInvalid("{\"version\":1}");
        assertInvalid("{\"template\":{\"params\":{}}}");
        assertInvalid("{\"template\":{\"method\":\" \"}}");
    }

    @Test
    void should_reject_illegal_path_or_excessive_depth() {
        assertInvalid("{\"template\":{\"method\":\"m\",\"params\":{\"a\":\"$..bad\"}}}");

        StringBuilder deep = new StringBuilder("{\"template\":{\"method\":\"m\",\"params\":{\"a\":");
        for (int i = 0; i < 12; i++) {
            deep.append("{\"x\":");
        }
        deep.append("1");
        for (int i = 0; i < 12; i++) {
            deep.append("}");
        }
        deep.append("}}}");
        assertInvalid(deep.toString());
    }

    @Test
    void should_reject_oversized_codec() {
        String huge = "{\"template\":{\"method\":\"m\",\"params\":{\"x\":\""
                + "a".repeat(PayloadCodecValidator.MAX_SIZE_BYTES) + "\"}}}";
        assertInvalid(huge);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> PayloadCodecValidator.normalize(json, mapper))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6317));
    }
}