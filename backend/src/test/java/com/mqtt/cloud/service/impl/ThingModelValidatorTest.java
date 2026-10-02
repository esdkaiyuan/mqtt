package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 物模型校验规则穷举（V1~V16，见 T-14 设计文档 §5）。
 * <p>
 * 每条用例只破坏一条规则，断言错误路径精确命中，避免规则之间互相掩盖。
 */
class ThingModelValidatorTest {

    private static final String PROP_TEMP = """
            {"identifier":"temperature","name":"环境温度",
             "dataType":{"type":"float","min":-40,"max":125,"step":0.1,"unit":"℃"},"accessMode":"r"}
            """;

    private static final String EVENT_ALARM = """
            {"identifier":"highTempAlarm","name":"高温告警","type":"alert",
             "outputData":[{"identifier":"value","name":"触发温度","dataType":{"type":"float","unit":"℃"}}]}
            """;

    private static final String SERVICE_MODE = """
            {"identifier":"setMode","name":"设置工作模式","callType":"async",
             "inputData":[{"identifier":"mode","name":"工作模式","required":true,
                           "dataType":{"type":"enum","specs":{"0":"自动","1":"手动"}}}],
             "outputData":[{"identifier":"accepted","name":"是否接受","dataType":{"type":"bool"}}]}
            """;

    private static String tsl(String properties, String events, String services) {
        return """
                {"schemaVersion":"1.0","properties":[%s],"events":[%s],"services":[%s]}
                """.formatted(properties, events, services);
    }

    private static List<String> paths(ThingModelValidationResult result) {
        return result.errors().stream().map(e -> e.path()).toList();
    }

    @Test
    void 完整合法物模型应通过() {
        ThingModelValidationResult result = ThingModelValidator.validate(
                tsl(PROP_TEMP, EVENT_ALARM, SERVICE_MODE));

        assertThat(result.valid()).isTrue();
        assertThat(result.parseFailed()).isFalse();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void V1_schemaVersion_必须为1_0() {
        String json = """
                {"schemaVersion":"2.0","properties":[],"events":[],"services":[]}
                """;

        assertThat(paths(ThingModelValidator.validate(json))).containsExactly("schemaVersion");
    }

    @Test
    void V2_三个集合必须存在且为数组() {
        assertThat(paths(ThingModelValidator.validate("{\"schemaVersion\":\"1.0\"}")))
                .contains("properties", "events", "services");

        String json = """
                {"schemaVersion":"1.0","properties":{},"events":[],"services":[]}
                """;
        assertThat(paths(ThingModelValidator.validate(json))).containsExactly("properties");
    }

    @Test
    void V2_集合元素必须为对象() {
        String json = tsl("123", "", "");

        assertThat(paths(ThingModelValidator.validate(json))).containsExactly("properties[0]");
    }

    @Test
    void V3_identifier_必须匹配命名规范() {
        String bad = """
                {"identifier":"1-temperature","name":"温度","dataType":{"type":"bool"},"accessMode":"r"}
                """;

        assertThat(paths(ThingModelValidator.validate(tsl(bad, "", ""))))
                .containsExactly("properties[0].identifier");
    }

    @Test
    void V4_identifier_在物模型内全局唯一() {
        String duplicate = """
                {"identifier":"temperature","name":"重复温度","type":"info"}
                """;

        // 属性与事件跨集合重名，应命中事件侧
        assertThat(paths(ThingModelValidator.validate(tsl(PROP_TEMP, duplicate, ""))))
                .containsExactly("events[0].identifier");
    }

    @Test
    void V5_identifier_不得为保留字() {
        String reserved = """
                {"identifier":"method","name":"保留字","dataType":{"type":"bool"},"accessMode":"r"}
                """;

        assertThat(paths(ThingModelValidator.validate(tsl(reserved, "", ""))))
                .containsExactly("properties[0].identifier");
    }

    @Test
    void V6_数量上限() {
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            if (i > 0) {
                properties.append(',');
            }
            properties.append("{\"identifier\":\"p").append(i)
                    .append("\",\"name\":\"属性\",\"dataType\":{\"type\":\"bool\"},\"accessMode\":\"r\"}");
        }

        assertThat(paths(ThingModelValidator.validate(tsl(properties.toString(), "", ""))))
                .containsExactly("properties");
    }

    @Test
    void V7_name_非空且长度受限() {
        String noName = """
                {"identifier":"temperature","dataType":{"type":"bool"},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(noName, "", ""))))
                .containsExactly("properties[0].name");

        String longName = """
                {"identifier":"temperature","name":"%s","dataType":{"type":"bool"},"accessMode":"r"}
                """.formatted("n".repeat(65));
        assertThat(paths(ThingModelValidator.validate(tsl(longName, "", ""))))
                .containsExactly("properties[0].name");

        String longDescription = """
                {"identifier":"temperature","name":"温度","description":"%s",
                 "dataType":{"type":"bool"},"accessMode":"r"}
                """.formatted("d".repeat(256));
        assertThat(paths(ThingModelValidator.validate(tsl(longDescription, "", ""))))
                .containsExactly("properties[0].description");
    }

    @Test
    void V8_dataType_type_必须在白名单内() {
        String bad = """
                {"identifier":"temperature","name":"温度","dataType":{"type":"string"},"accessMode":"r"}
                """;

        assertThat(paths(ThingModelValidator.validate(tsl(bad, "", ""))))
                .containsExactly("properties[0].dataType.type");
    }

    @Test
    void V9_数值约束_min不得大于max_step必须为正_int必须为整数() {
        String inverted = """
                {"identifier":"temperature","name":"温度",
                 "dataType":{"type":"float","min":10,"max":1},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(inverted, "", ""))))
                .containsExactly("properties[0].dataType");

        String zeroStep = """
                {"identifier":"temperature","name":"温度",
                 "dataType":{"type":"float","step":0},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(zeroStep, "", ""))))
                .containsExactly("properties[0].dataType.step");

        String fractionalInt = """
                {"identifier":"temperature","name":"温度",
                 "dataType":{"type":"int","step":0.5},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(fractionalInt, "", ""))))
                .containsExactly("properties[0].dataType.step");
    }

    @Test
    void V10_enum_specs_非空且键长度受限() {
        String empty = """
                {"identifier":"mode","name":"模式","dataType":{"type":"enum","specs":{}},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(empty, "", ""))))
                .containsExactly("properties[0].dataType.specs");

        String longKey = """
                {"identifier":"mode","name":"模式",
                 "dataType":{"type":"enum","specs":{"%s":"值"}},"accessMode":"r"}
                """.formatted("k".repeat(33));
        assertThat(paths(ThingModelValidator.validate(tsl(longKey, "", ""))))
                .containsExactly("properties[0].dataType.specs");
    }

    @Test
    void V11_struct_非空_成员唯一_深度受限() {
        String empty = """
                {"identifier":"cfg","name":"配置","dataType":{"type":"struct","specs":[]},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(empty, "", ""))))
                .containsExactly("properties[0].dataType.specs");

        String duplicateMember = """
                {"identifier":"cfg","name":"配置","dataType":{"type":"struct","specs":[
                  {"identifier":"m","name":"成员","dataType":{"type":"bool"}},
                  {"identifier":"m","name":"成员","dataType":{"type":"bool"}}]},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(duplicateMember, "", ""))))
                .containsExactly("properties[0].dataType.specs[1].identifier");

        String tooDeep = """
                {"identifier":"cfg","name":"配置","dataType":%s,"accessMode":"r"}
                """.formatted(nestedStruct(5));
        assertThat(paths(ThingModelValidator.validate(tsl(tooDeep, "", ""))))
                .containsExactly("properties[0].dataType.specs[0].dataType.specs[0].dataType.specs[0]"
                        + ".dataType.specs[0].dataType");
    }

    @Test
    void V11_四层嵌套结构体应通过() {
        String ok = """
                {"identifier":"cfg","name":"配置","dataType":%s,"accessMode":"r"}
                """.formatted(nestedStruct(4));

        assertThat(ThingModelValidator.validate(tsl(ok, "", "")).valid()).isTrue();
    }

    @Test
    void V12_array_必须带item() {
        String noItem = """
                {"identifier":"samples","name":"采样","dataType":{"type":"array"},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(noItem, "", ""))))
                .containsExactly("properties[0].dataType.item");
    }

    @Test
    void V13_text_length_必须在区间内() {
        String zero = """
                {"identifier":"label","name":"标签","dataType":{"type":"text","length":0},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(zero, "", ""))))
                .containsExactly("properties[0].dataType.length");

        String missing = """
                {"identifier":"label","name":"标签","dataType":{"type":"text"},"accessMode":"r"}
                """;
        assertThat(paths(ThingModelValidator.validate(tsl(missing, "", ""))))
                .containsExactly("properties[0].dataType.length");
    }

    @Test
    void V14_属性_accessMode_取值受限() {
        String bad = """
                {"identifier":"temperature","name":"温度","dataType":{"type":"bool"},"accessMode":"w"}
                """;

        assertThat(paths(ThingModelValidator.validate(tsl(bad, "", ""))))
                .containsExactly("properties[0].accessMode");
    }

    @Test
    void V15_事件_type_取值受限() {
        String bad = """
                {"identifier":"alarm","name":"告警","type":"warning"}
                """;

        assertThat(paths(ThingModelValidator.validate(tsl("", bad, ""))))
                .containsExactly("events[0].type");
    }

    @Test
    void V16_服务_callType_取值受限() {
        String bad = """
                {"identifier":"setMode","name":"设置模式","callType":"fire_and_forget"}
                """;

        assertThat(paths(ThingModelValidator.validate(tsl("", "", bad))))
                .containsExactly("services[0].callType");
    }

    @Test
    void 服务入参_required_必须为布尔() {
        String bad = """
                {"identifier":"setMode","name":"设置模式","callType":"sync",
                 "inputData":[{"identifier":"mode","name":"模式","required":"yes",
                               "dataType":{"type":"bool"}}]}
                """;

        assertThat(paths(ThingModelValidator.validate(tsl("", "", bad))))
                .containsExactly("services[0].inputData[0].required");
    }

    @Test
    void JSON_解析失败单独归类() {
        ThingModelValidationResult result = ThingModelValidator.validate("{not json");

        assertThat(result.parseFailed()).isTrue();
        assertThat(result.valid()).isFalse();
    }

    @Test
    void 空白内容视为解析失败() {
        assertThat(ThingModelValidator.validate("  ").parseFailed()).isTrue();
    }

    @Test
    void 非对象根节点应被拒绝() {
        ThingModelValidationResult result = ThingModelValidator.validate("[1,2,3]");

        assertThat(result.valid()).isFalse();
        assertThat(result.parseFailed()).isFalse();
        assertThat(paths(result)).containsExactly("$");
    }

    @Test
    void parseDefinition_投影属性与事件() {
        ThingModelDefinition definition = ThingModelValidator.parseDefinition(
                tsl(PROP_TEMP, EVENT_ALARM, SERVICE_MODE), 3);

        assertThat(definition.version()).isEqualTo(3);
        assertThat(definition.properties()).containsOnlyKeys("temperature");
        assertThat(definition.properties().get("temperature").type()).isEqualTo("float");
        assertThat(definition.properties().get("temperature").integer()).isFalse();
        assertThat(definition.properties().get("temperature").min()).isEqualByComparingTo("-40");
        assertThat(definition.events()).containsOnlyKeys("highTempAlarm");
        assertThat(definition.events().get("highTempAlarm").eventType()).isEqualTo("alert");
    }

    @Test
    void parseDefinition_枚举与整型约束被保留() {
        String property = """
                {"identifier":"mode","name":"模式",
                 "dataType":{"type":"enum","specs":{"0":"自动","1":"手动"}},"accessMode":"r"}
                """;
        String counter = """
                {"identifier":"count","name":"计数",
                 "dataType":{"type":"int","min":0,"max":100,"step":1},"accessMode":"r"}
                """;

        ThingModelDefinition definition = ThingModelValidator.parseDefinition(
                tsl(property + "," + counter, "", ""), 1);

        assertThat(definition.properties().get("mode").enumKeys()).containsExactlyInAnyOrder("0", "1");
        assertThat(definition.properties().get("count").integer()).isTrue();
        assertThat(definition.properties().get("count").max()).isEqualByComparingTo("100");
    }

    @Test
    void parseDefinition_非法JSON退化为空模型() {
        assertThat(ThingModelValidator.parseDefinition("{bad", 5).isEmpty()).isTrue();
        assertThat(ThingModelValidator.parseDefinition(null, 5)).isEqualTo(ThingModelDefinition.EMPTY);
    }

    @Test
    void parseDefinition_投影服务与访问模式() {
        ThingModelDefinition definition = ThingModelValidator.parseDefinition(
                tsl(PROP_TEMP, EVENT_ALARM, SERVICE_MODE), 4);

        assertThat(definition.services()).containsOnlyKeys("setMode");
        ThingModelDefinition.ServiceSpec service = definition.services().get("setMode");
        assertThat(service.callType()).isEqualTo("async");
        assertThat(service.input()).containsOnlyKeys("mode");
        assertThat(service.input().get("mode").required()).isTrue();
        assertThat(service.input().get("mode").enumKeys()).containsExactlyInAnyOrder("0", "1");
        assertThat(service.output()).containsOnlyKeys("accepted");
        assertThat(service.output().get("accepted").type()).isEqualTo("bool");
        assertThat(service.output().get("accepted").required()).isFalse();

        // 只读属性的 accessMode 被投影且 writable=false
        assertThat(definition.properties().get("temperature").accessMode()).isEqualTo("r");
        assertThat(definition.properties().get("temperature").writable()).isFalse();
    }

    @Test
    void parseDefinition_可写属性投影为rw() {
        String rw = """
                {"identifier":"power","name":"电源","dataType":{"type":"bool"},"accessMode":"rw"}
                """;

        ThingModelDefinition definition = ThingModelValidator.parseDefinition(tsl(rw, "", ""), 1);

        assertThat(definition.properties().get("power").accessMode()).isEqualTo("rw");
        assertThat(definition.properties().get("power").writable()).isTrue();
    }

    @Test
    void parseDefinition_服务缺失字段静默跳过() {
        String noCallType = """
                {"identifier":"reboot","name":"重启","inputData":[]}
                """;

        ThingModelDefinition definition = ThingModelValidator.parseDefinition(tsl("", "", noCallType), 1);

        assertThat(definition.services()).containsOnlyKeys("reboot");
        assertThat(definition.services().get("reboot").callType()).isNull();
        assertThat(definition.services().get("reboot").input()).isEmpty();
    }

    /** 构造 {@code depth} 层 struct 包裹的 int，用于验证递归深度上限。 */
    private static String nestedStruct(int depth) {
        String inner = "{\"type\":\"int\",\"min\":0,\"max\":10}";
        for (int i = 0; i < depth; i++) {
            inner = "{\"type\":\"struct\",\"specs\":[{\"identifier\":\"m" + i + "\",\"name\":\"m" + i
                    + "\",\"dataType\":" + inner + "}]}";
        }
        return inner;
    }
}