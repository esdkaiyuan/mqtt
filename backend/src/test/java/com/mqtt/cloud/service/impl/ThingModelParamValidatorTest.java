package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.service.ThingModelDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 物模型值校验与文本化单测（T-15 实施计划 P8）。
 * <p>
 * 该工具是上行解析与下行命令校验的<b>唯一</b>实现：本层穷举各 dataType 的
 * 「合法 → 文本」与「非法 → 拒绝」两种语义，避免两条链路各自维护类型规则而漂移。
 */
class ThingModelParamValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String raw) {
        return MAPPER.readTree(raw);
    }

    private static ThingModelDefinition.PropertySpec prop(String type, BigDecimal min, BigDecimal max,
                                                          Set<String> enumKeys, Integer textLength) {
        return new ThingModelDefinition.PropertySpec("p", type, min, max, "int".equals(type), enumKeys, textLength, "rw");
    }

    private static ThingModelDefinition.ParamSpec param(String type, BigDecimal min, BigDecimal max,
                                                        Set<String> enumKeys, Integer textLength, boolean required) {
        return new ThingModelDefinition.ParamSpec("p", type, min, max, "int".equals(type), enumKeys, textLength, required);
    }

    // ---------- int ----------

    @Test
    void int_应接受范围内整数并去尾零() {
        ThingModelDefinition.PropertySpec spec = prop("int", BigDecimal.ZERO, BigDecimal.valueOf(100), Set.of(), null);

        assertThat(ThingModelParamValidator.normalize(spec, json("25"))).isEqualTo("25");
        assertThat(ThingModelParamValidator.normalize(spec, json("25.0"))).isEqualTo("25");
        assertThat(ThingModelParamValidator.validate(spec, json("0"))).isNull();
        assertThat(ThingModelParamValidator.validate(spec, json("100"))).isNull();
    }

    @Test
    void int_应拒绝小数_越界_非数字() {
        ThingModelDefinition.PropertySpec spec = prop("int", BigDecimal.ZERO, BigDecimal.valueOf(100), Set.of(), null);

        assertThat(ThingModelParamValidator.validate(spec, json("25.5"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("101"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("-1"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("\"25\""))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("true"))).isNotNull();
    }

    // ---------- float / double ----------

    @Test
    void float_应接受范围内数值并规整尾零() {
        ThingModelDefinition.PropertySpec spec = prop("float", BigDecimal.valueOf(-40), BigDecimal.valueOf(125), Set.of(), null);

        assertThat(ThingModelParamValidator.normalize(spec, json("25.50"))).isEqualTo("25.5");
        assertThat(ThingModelParamValidator.normalize(spec, json("-40"))).isEqualTo("-40");
        assertThat(ThingModelParamValidator.validate(spec, json("125"))).isNull();
    }

    @Test
    void float_应拒绝越界与非数字() {
        ThingModelDefinition.PropertySpec spec = prop("double", BigDecimal.valueOf(-40), BigDecimal.valueOf(125), Set.of(), null);

        assertThat(ThingModelParamValidator.validate(spec, json("125.1"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("-40.01"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("\"warm\""))).isNotNull();
    }

    // ---------- bool ----------

    @Test
    void bool_应接受布尔与0_1数值() {
        ThingModelDefinition.PropertySpec spec = prop("bool", null, null, Set.of(), null);

        assertThat(ThingModelParamValidator.normalize(spec, json("true"))).isEqualTo("true");
        assertThat(ThingModelParamValidator.normalize(spec, json("false"))).isEqualTo("false");
        assertThat(ThingModelParamValidator.normalize(spec, json("1"))).isEqualTo("true");
        assertThat(ThingModelParamValidator.normalize(spec, json("0"))).isEqualTo("false");
    }

    @Test
    void bool_应拒绝非0_1数值与文本() {
        ThingModelDefinition.PropertySpec spec = prop("bool", null, null, Set.of(), null);

        assertThat(ThingModelParamValidator.validate(spec, json("2"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("1.5"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("\"yes\""))).isNotNull();
    }

    // ---------- text ----------

    @Test
    void text_应接受限长内文本并拒绝超长与非文本() {
        ThingModelDefinition.PropertySpec spec = prop("text", null, null, Set.of(), 5);

        assertThat(ThingModelParamValidator.normalize(spec, json("\"abc\""))).isEqualTo("abc");
        assertThat(ThingModelParamValidator.normalize(spec, json("\"abcde\""))).isEqualTo("abcde");
        assertThat(ThingModelParamValidator.validate(spec, json("\"abcdef\""))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("123"))).isNotNull();
    }

    // ---------- enum ----------

    @Test
    void enum_应接受定义内键并拒绝未知键() {
        ThingModelDefinition.PropertySpec spec = prop("enum", null, null, Set.of("auto", "manual"), null);

        assertThat(ThingModelParamValidator.normalize(spec, json("\"auto\""))).isEqualTo("auto");
        assertThat(ThingModelParamValidator.validate(spec, json("\"other\""))).isNotNull();
        assertThat(ThingModelParamValidator.validate(spec, json("1"))).isNotNull();
    }

    // ---------- date ----------

    @Test
    void date_应接受标量并拒绝对象() {
        ThingModelDefinition.PropertySpec spec = prop("date", null, null, Set.of(), null);

        assertThat(ThingModelParamValidator.normalize(spec, json("\"2026-10-02\""))).isEqualTo("2026-10-02");
        assertThat(ThingModelParamValidator.normalize(spec, json("1735689600000"))).isEqualTo("1735689600000");
        assertThat(ThingModelParamValidator.validate(spec, json("{\"a\":1}"))).isNotNull();
    }

    // ---------- struct / array ----------

    @Test
    void struct_与_array_应接受对象或数组并文本化() {
        ThingModelDefinition.PropertySpec struct = prop("struct", null, null, Set.of(), null);
        ThingModelDefinition.PropertySpec array = prop("array", null, null, Set.of(), null);

        assertThat(ThingModelParamValidator.normalize(struct, json("{\"a\":1}"))).isEqualTo("{\"a\":1}");
        assertThat(ThingModelParamValidator.normalize(array, json("[1,2]"))).isEqualTo("[1,2]");
        assertThat(ThingModelParamValidator.validate(struct, json("[1,2]"))).isNull();
        assertThat(ThingModelParamValidator.validate(array, json("{\"a\":1}"))).isNull();
        assertThat(ThingModelParamValidator.validate(struct, json("123"))).isNotNull();
    }

    // ---------- 边界 ----------

    @Test
    void 未知类型与空值应被拒绝() {
        ThingModelDefinition.PropertySpec unknown = prop("mystery", null, null, Set.of(), null);

        assertThat(ThingModelParamValidator.normalize(unknown, json("1"))).isNull();
        assertThat(ThingModelParamValidator.validate(unknown, json("1"))).isNotNull();
        assertThat(ThingModelParamValidator.validate(prop("int", null, null, Set.of(), null), json("null"))).isNotNull();
    }

    // ---------- ParamSpec 重载 ----------

    @Test
    void 服务入参重载_应与属性语义一致() {
        ThingModelDefinition.ParamSpec spec = param("int", BigDecimal.ZERO, BigDecimal.TEN, Set.of(), null, true);

        assertThat(ThingModelParamValidator.normalize(spec, json("7"))).isEqualTo("7");
        assertThat(ThingModelParamValidator.validate(spec, json("7"))).isNull();
        assertThat(ThingModelParamValidator.validate(spec, json("11"))).isNotNull();
    }
}