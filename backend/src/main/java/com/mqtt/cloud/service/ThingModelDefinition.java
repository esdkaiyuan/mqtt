package com.mqtt.cloud.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * 物模型的运行时视图：只保留解析上行与下发命令所需的定义，供摄取解析链路与 T-15/T-16 复用。
 * <p>
 * 与 TSL 原文（{@code product.thing_model}）分离：原文是存储与编辑的对象，本视图是校验通过的只读投影，
 * 因此不暴露 Jackson 类型，缓存可安全地跨线程复用。
 */
public record ThingModelDefinition(
        int version,
        Map<String, PropertySpec> properties,
        Map<String, EventSpec> events,
        Map<String, ServiceSpec> services) {

    /** 未建模（或解析开关关闭）时的空模型，所有解析动作按「未建模」跳过。 */
    public static final ThingModelDefinition EMPTY = new ThingModelDefinition(0, Map.of(), Map.of(), Map.of());

    public ThingModelDefinition {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        events = events == null ? Map.of() : Map.copyOf(events);
        services = services == null ? Map.of() : Map.copyOf(services);
    }

    public boolean isEmpty() {
        return properties.isEmpty() && events.isEmpty() && services.isEmpty();
    }

    /**
     * 属性定义。数值类型带上界用于落库前的范围校验；{@code enumKeys} 用于枚举取值校验；
     * {@code textLength} 为文本最大长度；{@code accessMode} 决定该属性是否可被下行设置。
     */
    public record PropertySpec(String identifier,
                               String type,
                               BigDecimal min,
                               BigDecimal max,
                               boolean integer,
                               Set<String> enumKeys,
                               Integer textLength,
                               String accessMode) {

        public PropertySpec {
            enumKeys = enumKeys == null ? Set.of() : Set.copyOf(enumKeys);
        }

        /** 是否可写（下行属性设置的目标）。TSL 中 {@code accessMode} 仅允许 r / rw。 */
        public boolean writable() {
            return "rw".equals(accessMode);
        }
    }

    /** 事件定义：落库时 {@code eventType} 取自当时的物模型，而非上报载荷。 */
    public record EventSpec(String identifier, String eventType) {
    }

    /**
     * 服务入参 / 出参定义。字段与 {@link PropertySpec} 一致，额外带 {@code required}
     * （仅入参有意义：必填校验）。
     */
    public record ParamSpec(String identifier,
                            String type,
                            BigDecimal min,
                            BigDecimal max,
                            boolean integer,
                            Set<String> enumKeys,
                            Integer textLength,
                            boolean required) {

        public ParamSpec {
            enumKeys = enumKeys == null ? Set.of() : Set.copyOf(enumKeys);
        }
    }

    /** 服务定义：{@code callType} 为 sync / async，{@code input} 为入参、{@code output} 为出参。 */
    public record ServiceSpec(String identifier,
                              String callType,
                              Map<String, ParamSpec> input,
                              Map<String, ParamSpec> output) {

        public ServiceSpec {
            input = input == null ? Map.of() : Map.copyOf(input);
            output = output == null ? Map.of() : Map.copyOf(output);
        }
    }
}