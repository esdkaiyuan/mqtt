package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 可保存看板配置（T-21 设计文档 §9.2）。
 * <p>
 * 前缀 {@code app.dashboard}，字段全部带默认值，风格对齐 {@link DeviceLogProperties}。
 * 两项均为<strong>只加不减的上限保护</strong>：置 0 / 负数时由服务层夹取为常量默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.dashboard")
public class DashboardProperties {

    /** 单用户看板数量上限；超出报 6230 */
    private int maxCount = 20;

    /** 单看板面板数量上限；超出报 6229 */
    private int maxPanels = 12;
}
