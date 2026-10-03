package com.mqtt.cloud.util;

import java.util.Map;

/**
 * 规则载荷模板渲染（T-19 设计文档 §8.7）。
 * <p>
 * 纯字符串替换：把 {@code ${name}} 占位符替换为**经 JSON 转义**的取值，未知占位符保留原样。
 * 采用**单次扫描**（不递归替换替换结果），避免用户构造的占位符注入；不引入表达式 / 脚本引擎。
 * <p>
 * 支持占位符见 {@code RuleConstants.PLACEHOLDERS}；调用方传入的 {@code values} 键为不含
 * {@code ${}} 的名字（如 {@code deviceKey}）。
 */
public final class RuleTemplateRenderer {

    private RuleTemplateRenderer() {
    }

    /**
     * 渲染模板：识别并替换 {@code ${key}}，其余原样输出。
     *
     * @param template 原始模板，可空
     * @param values   占位符名 → 取值；取值为 {@code null} 时替换为空串
     * @return 渲染结果；{@code template} 为空时返回 {@code null}
     */
    public static String render(String template, Map<String, String> values) {
        if (template == null) {
            return null;
        }
        if (values == null || values.isEmpty() || template.indexOf("${") < 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 16);
        int cursor = 0;
        while (cursor < template.length()) {
            int start = template.indexOf("${", cursor);
            if (start < 0) {
                out.append(template, cursor, template.length());
                break;
            }
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                out.append(template, cursor, template.length());
                break;
            }
            out.append(template, cursor, start);
            String key = template.substring(start + 2, end);
            if (values.containsKey(key)) {
                out.append(jsonEscape(values.get(key)));
            } else {
                // 未知占位符保留原样，避免把用户的其他 ${...} 语义吃掉
                out.append(template, start, end + 1);
            }
            cursor = end + 1;
        }
        return out.toString();
    }

    /** JSON 字符串安全转义：转义引号 / 反斜杠 / 控制字符，保证嵌入 JSON 文本后仍合法。 */
    public static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}