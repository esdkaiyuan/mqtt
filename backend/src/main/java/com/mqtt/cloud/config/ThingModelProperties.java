package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 物模型与上行解析配置（T-14）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.thing-model")
public class ThingModelProperties {

    /**
     * 上行解析总开关。false 时整条解析链路退化为现状（只落原始消息，不写属性 / 事件派生表），
     * 用于故障回滚。
     */
    private boolean interpretEnabled = true;

    /**
     * 物模型定义缓存 TTL（秒），0 关闭缓存。
     * <p>保存物模型时只失效<b>本副本</b>缓存，多副本收敛窗口 = 该 TTL。
     */
    private long cacheTtlSeconds = 60;

    /** 物模型 TSL 大小上限（字节），保存与导入共用。默认 256 KB。 */
    private int maxSizeBytes = 262_144;
}