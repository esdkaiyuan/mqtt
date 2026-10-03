package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备统一日志查询边界配置（T-20 设计文档 §9.2）。
 * <p>
 * 前缀 {@code app.device-log}，字段全部带默认值，风格对齐 {@link RuleProperties} / {@link AlertProperties}。
 * 两项配置<strong>只影响查询边界</strong>：默认值即为安全值；置 0 / 负数时由服务层夹取为默认值，
 * 保证不会因配置缺失而失去保护。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.device-log")
public class DeviceLogProperties {

    /** 单页数量上限；请求 {@code pageSize} 超出时夹取为该值 */
    private int maxPageSize = 100;

    /** 时间窗跨度上限（天）；同时给出起止时间且超出时报 6223 */
    private int maxRangeDays = 31;
}
