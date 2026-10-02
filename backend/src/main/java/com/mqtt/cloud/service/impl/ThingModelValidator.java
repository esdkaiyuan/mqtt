package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelValidationError;
import com.mqtt.cloud.service.ThingModelValidationResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 物模型（TSL）校验与投影解析，纯函数、无副作用，便于穷举单测（见 T-14 设计文档 §5）。
 * <p>
 * 校验规则 V1~V16 全部实现；任一规则不通过即整份拒绝，并返回<b>全部</b>错误路径
 * （调用方取首个错误拼提示）。JSON 无法解析单独归类，对应错误码 6101。
 * <p>
 * 说明：JSON 对象键在解析后天然唯一，故 V10 的「枚举键唯一」由数据结构保证，
 * 此处只校验非空与键长度；struct 成员为数组，其唯一性仍需显式校验（V11）。
 */
public final class ThingModelValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SCHEMA_VERSION = "1.0";
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,31}$");
    private static final Set<String> RESERVED_IDENTIFIERS =
            Set.of("id", "version", "method", "params", "code", "data");
    private static final Set<String> DATA_TYPES =
            Set.of("int", "float", "double", "bool", "text", "date", "enum", "struct", "array");
    private static final Set<String> ACCESS_MODES = Set.of("r", "rw");
    private static final Set<String> EVENT_TYPES = Set.of("info", "alert", "fault");
    private static final Set<String> CALL_TYPES = Set.of("sync", "async");

    private static final int MAX_PROPERTIES = 200;
    private static final int MAX_EVENTS = 100;
    private static final int MAX_SERVICES = 100;
    private static final int MAX_NAME = 64;
    private static final int MAX_DESCRIPTION = 255;
    private static final int MAX_ENUM_KEY = 32;
    private static final int MAX_DEPTH = 5;
    private static final int MIN_TEXT_LENGTH = 1;
    private static final int MAX_TEXT_LENGTH = 10_240;

    private ThingModelValidator() {
    }

    /** 校验 TSL 原文，返回全部错误（或解析失败标记）。 */
    public static ThingModelValidationResult validate(String json) {
        if (json == null || json.isBlank()) {
            return ThingModelValidationResult.parseFailed("物模型内容为空");
        }
        JsonNode root;
        try {
            root = MAPPER.readValue(json, JsonNode.class);
        } catch (Exception e) {
            return ThingModelValidationResult.parseFailed(describe(e));
        }
        if (root == null || !root.isObject()) {
            return ThingModelValidationResult.invalid(
                    List.of(new ThingModelValidationError("$", "物模型必须是 JSON 对象")));
        }

        List<ThingModelValidationError> errors = new ArrayList<>();
        validateSchemaVersion(root, errors);
        List<JsonNode> properties = arrayField(root, "properties", errors);
        List<JsonNode> events = arrayField(root, "events", errors);
        List<JsonNode> services = arrayField(root, "services", errors);
        validateCounts(properties, events, services, errors);

        // V4：identifier 在属性 + 事件 + 服务范围内全局唯一
        Set<String> globalIdentifiers = new HashSet<>();
        for (int i = 0; i < properties.size(); i++) {
            validateProperty(properties.get(i), i, globalIdentifiers, errors);
        }
        for (int i = 0; i < events.size(); i++) {
            validateEvent(events.get(i), i, globalIdentifiers, errors);
        }
        for (int i = 0; i < services.size(); i++) {
            validateService(services.get(i), i, globalIdentifiers, errors);
        }

        return errors.isEmpty() ? ThingModelValidationResult.ok()
                : ThingModelValidationResult.invalid(errors);
    }

    /**
     * 把已校验的 TSL 原文投影为解析链路使用的 {@link ThingModelDefinition}。
     * <p>
     * 调用前应已通过 {@link #validate(String)}；此处对缺失字段做静默跳过，不抛异常，
     * 保证热路径（摄取解析）不会因脏数据中断。
     */
    public static ThingModelDefinition parseDefinition(String json, int version) {
        if (json == null || json.isBlank()) {
            return ThingModelDefinition.EMPTY;
        }
        JsonNode root;
        try {
            root = MAPPER.readValue(json, JsonNode.class);
        } catch (Exception e) {
            return ThingModelDefinition.EMPTY;
        }
        if (root == null || !root.isObject()) {
            return ThingModelDefinition.EMPTY;
        }
        return new ThingModelDefinition(version, parseProperties(root), parseEvents(root), parseServices(root));
    }

    private static Map<String, ThingModelDefinition.PropertySpec> parseProperties(JsonNode root) {
        JsonNode properties = root.get("properties");
        if (properties == null || !properties.isArray()) {
            return Map.of();
        }
        Map<String, ThingModelDefinition.PropertySpec> result = new LinkedHashMap<>();
        for (int i = 0; i < properties.size(); i++) {
            JsonNode node = properties.get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            String identifier = textField(node, "identifier");
            JsonNode dataType = node.get("dataType");
            if (identifier == null || dataType == null || !dataType.isObject()) {
                continue;
            }
            String type = textField(dataType, "type");
            if (type == null) {
                continue;
            }
            result.put(identifier, new ThingModelDefinition.PropertySpec(
                    identifier, type,
                    decimalOrNull(dataType.get("min")), decimalOrNull(dataType.get("max")),
                    "int".equals(type), enumKeys(dataType), intOrNull(dataType.get("length")),
                    textField(node, "accessMode")));
        }
        return result;
    }

    private static Map<String, ThingModelDefinition.ServiceSpec> parseServices(JsonNode root) {
        JsonNode services = root.get("services");
        if (services == null || !services.isArray()) {
            return Map.of();
        }
        Map<String, ThingModelDefinition.ServiceSpec> result = new LinkedHashMap<>();
        for (int i = 0; i < services.size(); i++) {
            JsonNode node = services.get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            String identifier = textField(node, "identifier");
            if (identifier == null) {
                continue;
            }
            result.put(identifier, new ThingModelDefinition.ServiceSpec(
                    identifier, textField(node, "callType"),
                    parseParams(node.get("inputData")), parseParams(node.get("outputData"))));
        }
        return result;
    }

    /** 服务入参 / 出参列表 → 标识符索引；{@code required} 仅入参有意义，缺省为 false。 */
    private static Map<String, ThingModelDefinition.ParamSpec> parseParams(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Map.of();
        }
        Map<String, ThingModelDefinition.ParamSpec> result = new LinkedHashMap<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode param = node.get(i);
            if (param == null || !param.isObject()) {
                continue;
            }
            String identifier = textField(param, "identifier");
            JsonNode dataType = param.get("dataType");
            if (identifier == null || dataType == null || !dataType.isObject()) {
                continue;
            }
            String type = textField(dataType, "type");
            if (type == null) {
                continue;
            }
            result.put(identifier, new ThingModelDefinition.ParamSpec(
                    identifier, type,
                    decimalOrNull(dataType.get("min")), decimalOrNull(dataType.get("max")),
                    "int".equals(type), enumKeys(dataType), intOrNull(dataType.get("length")),
                    booleanField(param, "required")));
        }
        return result;
    }

    private static Map<String, ThingModelDefinition.EventSpec> parseEvents(JsonNode root) {
        JsonNode events = root.get("events");
        if (events == null || !events.isArray()) {
            return Map.of();
        }
        Map<String, ThingModelDefinition.EventSpec> result = new LinkedHashMap<>();
        for (int i = 0; i < events.size(); i++) {
            JsonNode node = events.get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            String identifier = textField(node, "identifier");
            String type = textField(node, "type");
            if (identifier == null || type == null) {
                continue;
            }
            result.put(identifier, new ThingModelDefinition.EventSpec(identifier, type));
        }
        return result;
    }

    // ---------- V1 / V2 / V6 ----------

    private static void validateSchemaVersion(JsonNode root, List<ThingModelValidationError> errors) {
        String schemaVersion = textField(root, "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            errors.add(new ThingModelValidationError("schemaVersion",
                    "schemaVersion 必须为 \"" + SCHEMA_VERSION + "\""));
        }
    }

    private static List<JsonNode> arrayField(JsonNode root, String field,
                                             List<ThingModelValidationError> errors) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            errors.add(new ThingModelValidationError(field, field + " 必须存在（可为空数组）"));
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(new ThingModelValidationError(field, field + " 必须为数组"));
            return List.of();
        }
        List<JsonNode> elements = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            elements.add(node.get(i));
        }
        return elements;
    }

    private static void validateCounts(List<JsonNode> properties, List<JsonNode> events,
                                       List<JsonNode> services, List<ThingModelValidationError> errors) {
        if (properties.size() > MAX_PROPERTIES) {
            errors.add(new ThingModelValidationError("properties", "属性数量不得超过 " + MAX_PROPERTIES));
        }
        if (events.size() > MAX_EVENTS) {
            errors.add(new ThingModelValidationError("events", "事件数量不得超过 " + MAX_EVENTS));
        }
        if (services.size() > MAX_SERVICES) {
            errors.add(new ThingModelValidationError("services", "服务数量不得超过 " + MAX_SERVICES));
        }
    }

    // ---------- 属性 / 事件 / 服务 ----------

    private static void validateProperty(JsonNode node, int index, Set<String> globalIdentifiers,
                                         List<ThingModelValidationError> errors) {
        String path = "properties[" + index + "]";
        if (!node.isObject()) {
            errors.add(new ThingModelValidationError(path, "元素必须为对象"));
            return;
        }
        requireIdentifier(node, path, globalIdentifiers, errors);
        requireName(node, path, errors);
        validateDescription(node, path, errors);
        validateDataType(node.get("dataType"), path + ".dataType", 1, errors);

        String accessMode = textField(node, "accessMode");
        if (!ACCESS_MODES.contains(accessMode)) {
            errors.add(new ThingModelValidationError(path + ".accessMode", "accessMode 必须为 r 或 rw"));
        }
        requireBooleanIfPresent(node, "required", path, errors);
    }

    private static void validateEvent(JsonNode node, int index, Set<String> globalIdentifiers,
                                      List<ThingModelValidationError> errors) {
        String path = "events[" + index + "]";
        if (!node.isObject()) {
            errors.add(new ThingModelValidationError(path, "元素必须为对象"));
            return;
        }
        requireIdentifier(node, path, globalIdentifiers, errors);
        requireName(node, path, errors);
        validateDescription(node, path, errors);

        String type = textField(node, "type");
        if (!EVENT_TYPES.contains(type)) {
            errors.add(new ThingModelValidationError(path + ".type", "事件 type 必须为 info / alert / fault"));
        }
        validateParamList(node.get("outputData"), path + ".outputData", false, errors);
    }

    private static void validateService(JsonNode node, int index, Set<String> globalIdentifiers,
                                        List<ThingModelValidationError> errors) {
        String path = "services[" + index + "]";
        if (!node.isObject()) {
            errors.add(new ThingModelValidationError(path, "元素必须为对象"));
            return;
        }
        requireIdentifier(node, path, globalIdentifiers, errors);
        requireName(node, path, errors);
        validateDescription(node, path, errors);

        String callType = textField(node, "callType");
        if (!CALL_TYPES.contains(callType)) {
            errors.add(new ThingModelValidationError(path + ".callType", "服务 callType 必须为 sync / async"));
        }
        validateParamList(node.get("inputData"), path + ".inputData", true, errors);
        validateParamList(node.get("outputData"), path + ".outputData", false, errors);
    }

    /** 参数列表：事件输出参数、服务入参 / 出参共用。identifier 在列表内唯一。 */
    private static void validateParamList(JsonNode node, String path, boolean allowRequired,
                                          List<ThingModelValidationError> errors) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            errors.add(new ThingModelValidationError(path, "参数列表必须为数组"));
            return;
        }
        Set<String> identifiers = new HashSet<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode param = node.get(i);
            String paramPath = path + "[" + i + "]";
            if (param == null || !param.isObject()) {
                errors.add(new ThingModelValidationError(paramPath, "参数必须为对象"));
                continue;
            }
            requireIdentifier(param, paramPath, identifiers, errors);
            requireName(param, paramPath, errors);
            validateDataType(param.get("dataType"), paramPath + ".dataType", 1, errors);
            if (allowRequired) {
                requireBooleanIfPresent(param, "required", paramPath, errors);
            }
        }
    }

    // ---------- 数据类型（V8~V13） ----------

    private static void validateDataType(JsonNode dataType, String path, int depth,
                                         List<ThingModelValidationError> errors) {
        if (dataType == null || dataType.isNull() || !dataType.isObject()) {
            errors.add(new ThingModelValidationError(path, "dataType 必须为对象"));
            return;
        }
        String type = textField(dataType, "type");
        if (type == null || !DATA_TYPES.contains(type)) {
            errors.add(new ThingModelValidationError(path + ".type", "dataType.type 必须为 " + DATA_TYPES));
            return;
        }
        switch (type) {
            case "int", "float", "double" -> validateNumeric(dataType, path, type, errors);
            case "text" -> validateText(dataType, path, errors);
            case "enum" -> validateEnum(dataType, path, errors);
            case "struct" -> validateStruct(dataType, path, depth, errors);
            case "array" -> validateArray(dataType, path, depth, errors);
            default -> {
                // bool / date 无附加约束
            }
        }
        JsonNode unit = dataType.get("unit");
        if (unit != null && !unit.isNull() && !unit.isTextual()) {
            errors.add(new ThingModelValidationError(path + ".unit", "unit 必须为字符串"));
        }
    }

    private static void validateNumeric(JsonNode dataType, String path, String type,
                                        List<ThingModelValidationError> errors) {
        BigDecimal min = numberField(dataType, "min", path, errors);
        BigDecimal max = numberField(dataType, "max", path, errors);
        BigDecimal step = numberField(dataType, "step", path, errors);
        if (min != null && max != null && min.compareTo(max) > 0) {
            errors.add(new ThingModelValidationError(path, "min 不得大于 max"));
        }
        if (step != null && step.signum() <= 0) {
            errors.add(new ThingModelValidationError(path + ".step", "step 必须大于 0"));
        }
        if ("int".equals(type)) {
            requireIntegral(min, path + ".min", errors);
            requireIntegral(max, path + ".max", errors);
            requireIntegral(step, path + ".step", errors);
        }
    }

    private static void validateText(JsonNode dataType, String path, List<ThingModelValidationError> errors) {
        JsonNode length = dataType.get("length");
        if (length == null || length.isNull() || !length.isNumber()) {
            errors.add(new ThingModelValidationError(path + ".length", "text.length 必须存在且为整数"));
            return;
        }
        BigDecimal value = length.decimalValue();
        boolean integral = value.stripTrailingZeros().scale() <= 0;
        if (!integral || value.compareTo(BigDecimal.valueOf(MIN_TEXT_LENGTH)) < 0
                || value.compareTo(BigDecimal.valueOf(MAX_TEXT_LENGTH)) > 0) {
            errors.add(new ThingModelValidationError(path + ".length",
                    "text.length 必须在 [" + MIN_TEXT_LENGTH + ", " + MAX_TEXT_LENGTH + "] 内"));
        }
    }

    private static void validateEnum(JsonNode dataType, String path, List<ThingModelValidationError> errors) {
        JsonNode specs = dataType.get("specs");
        if (specs == null || specs.isNull() || !specs.isObject()) {
            errors.add(new ThingModelValidationError(path + ".specs", "enum.specs 必须为对象"));
            return;
        }
        if (specs.properties().isEmpty()) {
            errors.add(new ThingModelValidationError(path + ".specs", "enum.specs 不得为空"));
            return;
        }
        for (Map.Entry<String, JsonNode> entry : specs.properties()) {
            if (entry.getKey().length() > MAX_ENUM_KEY) {
                errors.add(new ThingModelValidationError(path + ".specs",
                        "枚举键长度不得超过 " + MAX_ENUM_KEY + " 字符: " + entry.getKey()));
            }
            JsonNode value = entry.getValue();
            if (value == null || value.isNull() || value.isObject() || value.isArray()) {
                errors.add(new ThingModelValidationError(path + ".specs." + entry.getKey(),
                        "枚举值必须为标量"));
            }
        }
    }

    private static void validateStruct(JsonNode dataType, String path, int depth,
                                       List<ThingModelValidationError> errors) {
        JsonNode specs = dataType.get("specs");
        if (specs == null || specs.isNull() || !specs.isArray()) {
            errors.add(new ThingModelValidationError(path + ".specs", "struct.specs 必须为数组"));
            return;
        }
        if (specs.isEmpty()) {
            errors.add(new ThingModelValidationError(path + ".specs", "struct.specs 不得为空"));
            return;
        }
        if (depth >= MAX_DEPTH) {
            errors.add(new ThingModelValidationError(path, "结构体嵌套深度超过上限 " + MAX_DEPTH));
            return;
        }
        Set<String> memberIdentifiers = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            JsonNode member = specs.get(i);
            String memberPath = path + ".specs[" + i + "]";
            if (member == null || !member.isObject()) {
                errors.add(new ThingModelValidationError(memberPath, "结构体成员必须为对象"));
                continue;
            }
            requireIdentifier(member, memberPath, memberIdentifiers, errors);
            requireName(member, memberPath, errors);
            validateDataType(member.get("dataType"), memberPath + ".dataType", depth + 1, errors);
        }
    }

    private static void validateArray(JsonNode dataType, String path, int depth,
                                      List<ThingModelValidationError> errors) {
        JsonNode item = dataType.get("item");
        if (item == null || item.isNull()) {
            errors.add(new ThingModelValidationError(path + ".item", "array.item 必须存在"));
            return;
        }
        if (depth >= MAX_DEPTH) {
            errors.add(new ThingModelValidationError(path, "数组嵌套深度超过上限 " + MAX_DEPTH));
            return;
        }
        validateDataType(item, path + ".item", depth + 1, errors);
    }

    // ---------- 通用字段 ----------

    private static String requireIdentifier(JsonNode node, String path, Set<String> scope,
                                            List<ThingModelValidationError> errors) {
        String identifier = textField(node, "identifier");
        if (identifier == null || identifier.isBlank()) {
            errors.add(new ThingModelValidationError(path + ".identifier", "identifier 必须为非空字符串"));
            return null;
        }
        if (!IDENTIFIER.matcher(identifier).matches()) {
            errors.add(new ThingModelValidationError(path + ".identifier",
                    "identifier 需匹配 ^[a-zA-Z][a-zA-Z0-9_]{0,31}$"));
            return null;
        }
        if (RESERVED_IDENTIFIERS.contains(identifier)) {
            errors.add(new ThingModelValidationError(path + ".identifier",
                    "identifier 不得为保留字: " + identifier));
            return null;
        }
        if (scope != null && !scope.add(identifier)) {
            errors.add(new ThingModelValidationError(path + ".identifier",
                    "identifier 在物模型内重复: " + identifier));
            return null;
        }
        return identifier;
    }

    private static void requireName(JsonNode node, String path, List<ThingModelValidationError> errors) {
        String name = textField(node, "name");
        if (name == null || name.isBlank()) {
            errors.add(new ThingModelValidationError(path + ".name", "name 不能为空"));
        } else if (name.length() > MAX_NAME) {
            errors.add(new ThingModelValidationError(path + ".name",
                    "name 长度不得超过 " + MAX_NAME + " 字符"));
        }
    }

    private static void validateDescription(JsonNode node, String path,
                                            List<ThingModelValidationError> errors) {
        JsonNode description = node.get("description");
        if (description != null && !description.isNull() && description.isTextual()
                && description.asText().length() > MAX_DESCRIPTION) {
            errors.add(new ThingModelValidationError(path + ".description",
                    "description 长度不得超过 " + MAX_DESCRIPTION + " 字符"));
        }
    }

    private static void requireBooleanIfPresent(JsonNode node, String field, String path,
                                                List<ThingModelValidationError> errors) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull() && !value.isBoolean()) {
            errors.add(new ThingModelValidationError(path + "." + field, field + " 必须为布尔值"));
        }
    }

    private static String textField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private static boolean booleanField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private static BigDecimal numberField(JsonNode node, String field, String path,
                                          List<ThingModelValidationError> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            errors.add(new ThingModelValidationError(path + "." + field, field + " 必须为数字"));
            return null;
        }
        return value.decimalValue();
    }

    private static void requireIntegral(BigDecimal value, String path,
                                        List<ThingModelValidationError> errors) {
        if (value != null && value.stripTrailingZeros().scale() > 0) {
            errors.add(new ThingModelValidationError(path, "int 类型的 min/max/step 必须为整数"));
        }
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return (node != null && node.isNumber()) ? node.decimalValue() : null;
    }

    private static Integer intOrNull(JsonNode node) {
        return (node != null && node.isNumber()) ? node.asInt() : null;
    }

    private static Set<String> enumKeys(JsonNode dataType) {
        JsonNode specs = dataType.get("specs");
        if (specs == null || !specs.isObject()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, JsonNode> entry : specs.properties()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }
}