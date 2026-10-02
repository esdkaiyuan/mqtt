package com.mqtt.cloud.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物模型解析指标：{@code thing_model_interpret_total{result=...}}。
 * <p>
 * result 取值见 T-14 设计文档 §7.4：property / event / unparsed / unmodeled /
 * unknown_identifier / error。计数器按 result 缓存，避免热路径重复构建。
 */
@Component
public class ThingModelInterpretMetrics {

    private static final String METRIC = "thing_model_interpret";
    private static final String TAG_RESULT = "result";

    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public ThingModelInterpretMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void count(String result) {
        count(result, 1);
    }

    public void count(String result, int times) {
        if (times <= 0) {
            return;
        }
        counters.computeIfAbsent(result, key -> Counter.builder(METRIC)
                        .description("物模型解析结果计数")
                        .tag(TAG_RESULT, key)
                        .register(registry))
                .increment(times);
    }
}