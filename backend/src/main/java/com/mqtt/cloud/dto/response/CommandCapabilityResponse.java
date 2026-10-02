package com.mqtt.cloud.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 可下发能力响应（T-15 设计文档 §10.1）：供前端生成属性设置 / 服务调用的动态表单。
 * <p>
 * 无物模型时 {@code modeled=false} 且两个集合为空。
 */
public record CommandCapabilityResponse(boolean modeled,
                                        int version,
                                        List<Property> properties,
                                        List<Service> services) {

    public record Property(String identifier,
                           String type,
                           BigDecimal min,
                           BigDecimal max,
                           boolean integer,
                           List<String> enumKeys,
                           Integer textLength) {
    }

    public record Service(String identifier,
                          String callType,
                          List<Param> input) {
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
}