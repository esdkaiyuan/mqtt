package com.mqtt.cloud.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 上位机运行所需的只读物模型投影（T-38）。
 * <p>
 * 只暴露控件绑定与值解析需要的字段；不含 TSL 原文中的描述、输出参数等编辑信息。
 */
public record HmiModelVO(List<Property> properties, List<Service> services) {

    public HmiModelVO {
        properties = properties == null ? List.of() : List.copyOf(properties);
        services = services == null ? List.of() : List.copyOf(services);
    }

    public record Property(String identifier,
                           String type,
                           String accessMode,
                           BigDecimal min,
                           BigDecimal max,
                           boolean integer,
                           List<String> enumKeys,
                           Integer textLength) {
    }

    public record Param(String identifier,
                        String type,
                        BigDecimal min,
                        BigDecimal max,
                        boolean integer,
                        List<String> enumKeys,
                        Integer textLength,
                        boolean required) {
    }

    public record Service(String identifier, String callType, List<Param> input) {
    }
}
