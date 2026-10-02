package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.service.ThingModelDefinition;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 物模型值校验与文本化：上行解析与下行命令校验的<b>唯一</b>实现（T-14 §6.2 口径）。
 * <p>
 * 从 {@code ThingModelInterpretServiceImpl} 提取而来，避免「上行宽松跳过」与「下行严格拒绝」
 * 各自维护一套类型规则而产生漂移。两种语义共用同一套判定：
 * <ul>
 *     <li>{@link #normalize} —— 上行语义：合法返回文本形式，不合法返回 {@code null}（调用方跳过并计数）；</li>
 *     <li>{@link #validate} —— 下行语义：合法返回 {@code null}，不合法返回可直接回给用户的错误原因。</li>
 * </ul>
 * 文本化口径：bool → {@code true}/{@code false}，数值 → 十进制字符串，
 * enum → 键，struct/array → JSON 文本。
 */
public final class ThingModelParamValidator {

    private static final String TYPE_INT = "int";
    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_DOUBLE = "double";
    private static final String TYPE_BOOL = "bool";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_DATE = "date";
    private static final String TYPE_ENUM = "enum";
    private static final String TYPE_STRUCT = "struct";
    private static final String TYPE_ARRAY = "array";

    private ThingModelParamValidator() {
    }

    /** 上行语义：属性值 → 文本；不合法返回 {@code null}。 */
    public static String normalize(ThingModelDefinition.PropertySpec spec, JsonNode value) {
        return normalize(spec.type(), spec.min(), spec.max(), spec.enumKeys(), spec.textLength(), value);
    }

    /** 上行语义：服务入参值 → 文本；不合法返回 {@code null}。 */
    public static String normalize(ThingModelDefinition.ParamSpec spec, JsonNode value) {
        return normalize(spec.type(), spec.min(), spec.max(), spec.enumKeys(), spec.textLength(), value);
    }

    /** 下行语义：属性值校验，通过返回 {@code null}。 */
    public static String validate(ThingModelDefinition.PropertySpec spec, JsonNode value) {
        return describe(spec.type(), normalize(spec, value));
    }

    /** 下行语义：服务入参值校验，通过返回 {@code null}。 */
    public static String validate(ThingModelDefinition.ParamSpec spec, JsonNode value) {
        return describe(spec.type(), normalize(spec, value));
    }

    private static String describe(String type, String normalized) {
        if (normalized != null) {
            return null;
        }
        return "值不符合 " + type + " 类型定义（类型 / 范围 / 枚举 / 长度不匹配）";
    }

    private static String normalize(String type, BigDecimal min, BigDecimal max,
                                    Set<String> enumKeys, Integer textLength, JsonNode value) {
        if (type == null || value == null || value.isNull()) {
            return null;
        }
        return switch (type) {
            case TYPE_INT -> integral(value, min, max);
            case TYPE_FLOAT, TYPE_DOUBLE -> decimal(value, min, max);
            case TYPE_BOOL -> bool(value);
            case TYPE_TEXT -> textValue(value, textLength);
            case TYPE_ENUM -> enumValue(value, enumKeys);
            case TYPE_DATE -> scalar(value);
            case TYPE_STRUCT, TYPE_ARRAY -> (value.isObject() || value.isArray()) ? value.toString() : null;
            default -> null;
        };
    }

    private static String integral(JsonNode value, BigDecimal min, BigDecimal max) {
        if (!value.isNumber()) {
            return null;
        }
        BigDecimal number = value.decimalValue();
        if (number.stripTrailingZeros().scale() > 0) {
            return null;
        }
        return withinRange(number, min, max) ? number.toBigInteger().toString() : null;
    }

    private static String decimal(JsonNode value, BigDecimal min, BigDecimal max) {
        if (!value.isNumber()) {
            return null;
        }
        BigDecimal number = value.decimalValue();
        return withinRange(number, min, max) ? number.stripTrailingZeros().toPlainString() : null;
    }

    private static String bool(JsonNode value) {
        if (value.isBoolean()) {
            return value.asBoolean() ? "true" : "false";
        }
        if (value.isNumber()) {
            BigDecimal number = value.decimalValue();
            if (number.stripTrailingZeros().scale() <= 0) {
                int flag = number.intValue();
                if (flag == 0 || flag == 1) {
                    return flag == 1 ? "true" : "false";
                }
            }
        }
        return null;
    }

    private static String textValue(JsonNode value, Integer maxLength) {
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return (maxLength != null && text.length() > maxLength) ? null : text;
    }

    private static String enumValue(JsonNode value, Set<String> enumKeys) {
        String key = scalar(value);
        return (key != null && enumKeys != null && enumKeys.contains(key)) ? key : null;
    }

    /** 标量的文本形式：文本原样、数字取十进制、布尔取 true/false；其余返回 null。 */
    private static String scalar(JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber()) {
            return value.decimalValue().stripTrailingZeros().toPlainString();
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "true" : "false";
        }
        return null;
    }

    private static boolean withinRange(BigDecimal number, BigDecimal min, BigDecimal max) {
        if (min != null && number.compareTo(min) < 0) {
            return false;
        }
        return max == null || number.compareTo(max) <= 0;
    }
}