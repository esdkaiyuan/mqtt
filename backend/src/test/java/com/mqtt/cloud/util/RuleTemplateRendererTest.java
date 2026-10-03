package com.mqtt.cloud.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规则载荷模板渲染单测（T-19 实施计划 P7）。
 * <p>
 * 单次扫描替换（不递归），未知占位符保留原样；取值经 JSON 转义；模板为空返回 {@code null}。
 */
class RuleTemplateRendererTest {

    @Test
    void should_replace_known_placeholders() {
        Map<String, String> values = Map.of("deviceKey", "dev-1", "value", "42");

        String rendered = RuleTemplateRenderer.render("{\"d\":\"${deviceKey}\",\"v\":${value}}", values);

        assertThat(rendered).isEqualTo("{\"d\":\"dev-1\",\"v\":42}");
    }

    @Test
    void should_keep_unknown_placeholders_verbatim() {
        String rendered = RuleTemplateRenderer.render("${known} ${unknown}", Map.of("known", "x"));

        assertThat(rendered).isEqualTo("x ${unknown}");
    }

    @Test
    void should_return_null_for_null_template_and_passthrough_without_values() {
        assertThat(RuleTemplateRenderer.render(null, Map.of("a", "b"))).isNull();
        assertThat(RuleTemplateRenderer.render("plain text", Map.of())).isEqualTo("plain text");
        assertThat(RuleTemplateRenderer.render("${a}", null)).isEqualTo("${a}");
    }

    @Test
    void should_not_recursively_render_injected_placeholders() {
        // 取值里含 ${...} 时不二次替换，避免用户构造的占位符注入
        String rendered = RuleTemplateRenderer.render("${value}", Map.of("value", "${deviceKey}"));

        assertThat(rendered).isEqualTo("${deviceKey}");
    }

    @Test
    void should_json_escape_replaced_values() {
        Map<String, String> values = new HashMap<>();
        values.put("value", "a\"b\\c\nd");

        String rendered = RuleTemplateRenderer.render("${value}", values);

        assertThat(rendered).isEqualTo("a\\\"b\\\\c\\nd");
    }

    @Test
    void should_replace_null_value_with_empty_string() {
        Map<String, String> values = new HashMap<>();
        values.put("value", null);

        assertThat(RuleTemplateRenderer.render("[${value}]", values)).isEqualTo("[]");
    }

    @Test
    void json_escape_should_encode_control_chars() {
        assertThat(RuleTemplateRenderer.jsonEscape(null)).isEmpty();
        assertThat(RuleTemplateRenderer.jsonEscape("tab\there")).isEqualTo("tab\\there");
        assertThat(RuleTemplateRenderer.jsonEscape("\u0001")).isEqualTo("\\u0001");
    }
}