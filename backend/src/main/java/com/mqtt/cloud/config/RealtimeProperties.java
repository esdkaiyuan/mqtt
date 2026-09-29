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

    /** SSE 连接超时（毫秒），0 表示不超时。默认 30 分钟，避免客户端异常掉线后连接长期滞留 */
    private long streamTimeoutMs = 1_800_000L;

    /** 服务端心跳间隔（毫秒），以 SSE 注释帧穿透网关空闲超时并探测死连接；0 表示关闭心跳 */
    private long heartbeatIntervalMs = 15_000L;
}