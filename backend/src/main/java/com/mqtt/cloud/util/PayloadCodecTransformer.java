package com.mqtt.cloud.util;

import com.mqtt.cloud.service.PayloadCodecDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Payload Codec 模板渲染（T-55）。
 * <p>
 * 把非 Alink 原始报文按声明式模板映射成 Alink JSON。模板规则：
 * <ul>
 *   <li>对象 / 数组递归渲染；</li>
 *   <li>字符串以 {@code $} 开头视为路径表达式（如 {@code $.sensors.temp}、{@code $.list[0].value}）；</li>
 *   <li>其它值（字符串常量、数字、布尔、null）原样保留；</li>
 *   <li>路径不存在时该字段被省略，不注入 null。</li>
 * </ul>
 * 不支持脚本执行、函数调用或通配符，避免把设备上报变成任意代码执行入口。
 */
public final class PayloadCodecTransformer {

    private static final String FIELD_METHOD = "method";

    private PayloadCodecTransformer() {
    }

    /**
     * 应用 Codec。
     *
     * @return 映射后的 Alink 根节点；Codec 未启用时返回原节点；
     *         映射失败且 {@code fallbackToRaw=false} 时返回 {@code null}
     */
    public static JsonNode apply(JsonNode raw, PayloadCodecDefinition codec, ObjectMapper mapper) {
        if (raw == null || codec == null || !codec.enabled()) {
            return raw;
        }
        JsonNode result = render(codec.template(), raw, mapper);
        if (result == null || !result.isObject()) {
            return codec.fallbackToRaw() ? raw : null;
        }
        JsonNode method = result.get(FIELD_METHOD);
        if (method == null || !method.isTextual()) {
            return codec.fallbackToRaw() ? raw : null;
        }
        return result;
    }

    private static JsonNode render(JsonNode template, JsonNode raw, ObjectMapper mapper) {
        if (template == null) {
            return null;
        }
        if (template.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : template.properties()) {
                JsonNode value = render(entry.getValue(), raw, mapper);
                if (value != null) {
                    out.set(entry.getKey(), value);
                }
            }
            return out;
        }
        if (template.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            for (JsonNode item : template) {
                JsonNode value = render(item, raw, mapper);
                if (value != null) {
                    out.add(value);
                }
            }
            return out;
        }
        if (template.isTextual() && template.asText().startsWith("$")) {
            return resolve(raw, template.asText());
        }
        return template.deepCopy();
    }

    /**
     * 解析路径表达式。
     *
     * @return 命中的节点；路径不存在或语法非法返回 {@code null}
     */
    public static JsonNode resolve(JsonNode root, String expression) {
        List<PathStep> steps = parsePath(expression);
        if (steps == null || root == null) {
            return null;
        }
        JsonNode current = root;
        for (PathStep step : steps) {
            if (current == null) {
                return null;
            }
            if (step.field() != null) {
                if (!current.isObject()) {
                    return null;
                }
                current = current.get(step.field());
            } else {
                if (!current.isArray()) {
                    return null;
                }
                current = current.get(step.index());
            }
        }
        return current;
    }

    /** 路径是否合法（保存期校验使用；{@code $} 单独表示原始根节点）。 */
    public static boolean isValidPath(String expression) {
        return parsePath(expression) != null;
    }

    /**
     * 支持语法：{@code $}、{@code $.a.b}、{@code $[0]}、{@code $['a.b']}、{@code $.a[0].b}。
     */
    private static List<PathStep> parsePath(String expression) {
        if (expression == null) {
            return null;
        }
        String path = expression.trim();
        if (!path.startsWith("$")) {
            return null;
        }
        if (path.length() == 1) {
            return List.of();
        }
        List<PathStep> steps = new ArrayList<>();
        int index = 1;
        while (index < path.length()) {
            char c = path.charAt(index);
            if (c == '.') {
                int start = ++index;
                while (index < path.length() && path.charAt(index) != '.' && path.charAt(index) != '[') {
                    index++;
                }
                if (index == start) {
                    return null;
                }
                steps.add(new PathStep(path.substring(start, index), null));
            } else if (c == '[') {
                int end = path.indexOf(']', index + 1);
                if (end < 0) {
                    return null;
                }
                String token = path.substring(index + 1, end).trim();
                if (token.isEmpty()) {
                    return null;
                }
                if ((token.startsWith("'") && token.endsWith("'"))
                        || (token.startsWith("\"") && token.endsWith("\""))) {
                    String key = token.substring(1, token.length() - 1);
                    if (key.isEmpty()) {
                        return null;
                    }
                    steps.add(new PathStep(key, null));
                } else {
                    try {
                        int arrayIndex = Integer.parseInt(token);
                        if (arrayIndex < 0) {
                            return null;
                        }
                        steps.add(new PathStep(null, arrayIndex));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                index = end + 1;
            } else {
                return null;
            }
        }
        return steps;
    }

    private record PathStep(String field, Integer index) {
    }
}