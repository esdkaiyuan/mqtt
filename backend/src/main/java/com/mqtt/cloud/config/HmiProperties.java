package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 快速上位机配置（T-38）。
 * <p>
 * 前缀 {@code app.hmi}；两项均为只加不减的上限保护，非正数由服务层夹取为默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.hmi")
public class HmiProperties {

    /** 单用户上位机数量上限 */
    private int maxCount = 20;

    /** 单个上位机控件数量上限 */
    private int maxWidgets = 30;
}
