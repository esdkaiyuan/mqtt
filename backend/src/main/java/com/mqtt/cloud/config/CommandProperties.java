package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 命令下发与服务调用配置（T-15）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.command")
public class CommandProperties {

    /** 同步调用等待回执的超时（毫秒）。 */
    private long syncTimeoutMs = 5000;

    /** 同步等待的轮询间隔（毫秒）。 */
    private long pollIntervalMs = 200;

    /**
     * 超时巡检间隔（毫秒），0 关闭。
     * <p>巡检把长期停留 {@code PENDING/SENT} 的记录兜底置 {@code TIMEOUT}，
     * 保证异步命令也不会永远停在 {@code SENT}。
     */
    private long timeoutSweepIntervalMs = 30000;

    /** 异步命令判定超时的时长（毫秒）。 */
    private long asyncTimeoutMs = 60000;

    /** 命令参数 JSON 大小上限（字节），防止超大载荷压垮 Broker。 */
    private int maxParamsBytes = 16384;
}