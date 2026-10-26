package com.mqtt.cloud.util;

import com.mqtt.cloud.service.PayloadCodecDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PayloadCodecTransformer} 单元测试（T-55）。
 */
class PayloadCodecTransformerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_map_paths_constants_and_missing_fields() {
        PayloadCodecDefinition codec = codec("""
                {"template":{"method":"thing.event.property.post","params":{
                  "temperature":"$.sensors.temp",
                  "mode":"auto",
                  "nested":{"first":"$.list[0].value"},
                  "quoted":"$['a.b']",
                  "missing":"$.not.there"
                }}}
                """);
        JsonNode raw = read("""
                {"sensors":{"temp":25.5},"list":[{"value":7}],"a.b":"dot-key"}
                """);

        JsonNode result = PayloadCodecTransformer.apply(raw, codec, mapper);

        assertThat(result).isNotNull();
        assertThat(result.get("method").asText()).isEqualTo("thing.event.property.post");
        JsonNode params = result.get("params");
        assertThat(params.get("temperature").asDouble()).isEqualTo(25.5);
        assertThat(params.get("mode").asText()).isEqualTo("auto");
        assertThat(params.get("nested").get("first").asInt()).isEqualTo(7);
        assertThat(params.get("quoted").asText()).isEqualTo("dot-key");
        assertThat(params.has("missing")).isFalse();
    }

    @Test
    void should_return_raw_when_codec_disabled_or_raw_null() {
        assertThat(PayloadCodecTransformer.apply(read("{\"a\":1}"),
                PayloadCodecDefinition.EMPTY, mapper).get("a").asInt()).isEqualTo(1);
        assertThat(PayloadCodecTransformer.apply(null, codec("""
                {"template":{"method":"thing.event.property.post"}}
                """), mapper)).isNull();
    }

    @Test
    void should_fallback_to_raw_or_drop_when_method_missing() {
        PayloadCodecDefinition fallback = codec("""
                {"template":{"method":"$.no.method","params":{}}}
                """);
        JsonNode raw = read("{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":1}}");

        assertThat(PayloadCodecTransformer.apply(raw, fallback, mapper)).isEqualTo(raw);

        PayloadCodecDefinition strict = new PayloadCodecDefinition(fallback.template(), false);
        assertThat(PayloadCodecTransformer.apply(raw, strict, mapper)).isNull();
    }

    @Test
    void should_validate_path_syntax() {
        assertThat(PayloadCodecTransformer.isValidPath("$")).isTrue();
        assertThat(PayloadCodecTransformer.isValidPath("$.a.b[0].c")).isTrue();
        assertThat(PayloadCodecTransformer.isValidPath("$['a.b']")).isTrue();
        assertThat(PayloadCodecTransformer.isValidPath("a.b")).isFalse();
        assertThat(PayloadCodecTransformer.isValidPath("$..a")).isFalse();
        assertThat(PayloadCodecTransformer.isValidPath("$.a[abc]")).isFalse();
        assertThat(PayloadCodecTransformer.isValidPath("$.a[-1]")).isFalse();
    }

    private PayloadCodecDefinition codec(String json) {
        return PayloadCodecValidator.parseDefinition(json, mapper, true);
    }

    private JsonNode read(String json) {
        return mapper.readTree(json);
    }
}