package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.realtime")
public class RealtimeProperties {

    /** 迁移期是否保留前端直连 Broker 的受限账号 */
    private boolean directFrontendEnabled = false;
}