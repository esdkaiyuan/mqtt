package com.mqtt.cloud.util;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.service.PayloadCodecDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Payload Codec 校验与规范化（T-55）。
 * <p>
 * 只接受声明式模板，拒绝任意脚本。规范形：
 * <pre>
 * {
 *   "version": 1,
 *   "mode": "TEMPLATE",
 *   "fallbackToRaw": true,
 *   "template": {
 *     "method": "thing.event.property.post",
 *     "params": { "temperature": "$.sensors.temp" }
 *   }
 * }
 * </pre>
 * 校验项：JSON 对象、版本 1、mode=TEMPLATE、template 为对象且含 method、
 * 路径表达式合法、模板深度 / 节点数 / 原始大小受上限约束。
 */
public final class PayloadCodecValidator {

    /** Codec JSON 最大字节数。 */
    public static final int MAX_SIZE_BYTES = 16384;
    private static final int MAX_DEPTH = 10;
    private static final int MAX_NODES = 500;
    private static final String MODE_TEMPLATE = "TEMPLATE";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_FALLBACK = "fallbackToRaw";
    private static final String FIELD_TEMPLATE = "template";
    private static final String FIELD_METHOD = "method";

    private PayloadCodecValidator() {
    }

    /** 校验并返回规范化 JSON；空值返回 {@code null}。 */
    public static String normalize(String raw, ObjectMapper mapper) {
        Parsed parsed = parse(raw, mapper, true);
        return parsed == null ? null : parsed.normalized();
    }

    /**
     * 解析为运行时定义（用于产品读取 / 缓存）。
     *
     * @param strict true 时抛业务异常；false 时解析失败返回 {@code null}
     */
    public static PayloadCodecDefinition parseDefinition(String raw, ObjectMapper mapper, boolean strict) {
        Parsed parsed = parse(raw, mapper, strict);
        return parsed == null ? null : parsed.definition();
    }

    private static Parsed parse(String raw, ObjectMapper mapper, boolean strict) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_SIZE_BYTES) {
            throw invalid("Payload Codec 超过大小上限 " + MAX_SIZE_BYTES + " 字节");
        }
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            if (!strict) {
                return null;
            }
            throw invalid("Payload Codec 不是合法 JSON：" + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            if (!strict) {
                return null;
            }
            throw invalid("Payload Codec 必须是 JSON 对象");
        }
        JsonNode version = root.get(FIELD_VERSION);
        if (version != null && (!version.isInt() || version.asInt() != 1)) {
            if (!strict) {
                return null;
            }
            throw invalid("不支持的 Payload Codec 版本，仅支持 1");
        }
        JsonNode mode = root.get(FIELD_MODE);
        if (mode != null && (!mode.isTextual() || !MODE_TEMPLATE.equalsIgnoreCase(mode.asText()))) {
            if (!strict) {
                return null;
            }
            throw invalid("不支持的 Payload Codec mode，仅支持 TEMPLATE");
        }
        JsonNode fallback = root.get(FIELD_FALLBACK);
        if (fallback != null && !fallback.isBoolean()) {
            if (!strict) {
                return null;
            }
            throw invalid("fallbackToRaw 必须是布尔值");
        }
        JsonNode template = root.get(FIELD_TEMPLATE);
        if (template == null || !template.isObject()) {
            if (!strict) {
                return null;
            }
            throw invalid("Payload Codec 缺少 template 对象");
        }
        JsonNode method = template.get(FIELD_METHOD);
        if (method == null || !method.isTextual() || method.asText().isBlank()) {
            if (!strict) {
                return null;
            }
            throw invalid("template.method 不能为空");
        }
        if (method.asText().startsWith("$") && !PayloadCodecTransformer.isValidPath(method.asText())) {
            if (!strict) {
                return null;
            }
            throw invalid("template.method 路径表达式非法");
        }

        int[] nodeCount = {0};
        try {
            validateTemplate(template, 1, nodeCount);
        } catch (BusinessException e) {
            if (!strict) {
                return null;
            }
            throw e;
        }

        boolean fallbackToRaw = fallback == null || fallback.asBoolean();
        ObjectNode normalized = mapper.createObjectNode();
        normalized.put(FIELD_VERSION, 1);
        normalized.put(FIELD_MODE, MODE_TEMPLATE);
        normalized.put(FIELD_FALLBACK, fallbackToRaw);
        normalized.set(FIELD_TEMPLATE, template.deepCopy());
        return new Parsed(mapper.writeValueAsString(normalized),
                new PayloadCodecDefinition(template.deepCopy(), fallbackToRaw));
    }

    private static void validateTemplate(JsonNode node, int depth, int[] nodeCount) {
        if (depth > MAX_DEPTH) {
            throw invalid("template 嵌套深度超过 " + MAX_DEPTH);
        }
        if (++nodeCount[0] > MAX_NODES) {
            throw invalid("template 节点数超过 " + MAX_NODES);
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw invalid("template 字段名不能为空");
                }
                validateTemplate(entry.getValue(), depth + 1, nodeCount);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                validateTemplate(item, depth + 1, nodeCount);
            }
            return;
        }
        if (node.isTextual() && node.asText().startsWith("$")
                && !PayloadCodecTransformer.isValidPath(node.asText())) {
            throw invalid("路径表达式非法：" + node.asText());
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.PAYLOAD_CODEC_INVALID, message);
    }

    private record Parsed(String normalized, PayloadCodecDefinition definition) {
    }
}