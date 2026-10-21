package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台侧 Modbus 主站采集配置（T-31 P4）。
 * <p>
 * 默认关闭：未配置现场网络时不应自动出网轮询。所有上限都带默认值，
 * 现场可通过环境变量按总线能力收紧。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.modbus")
public class ModbusCollectProperties {

    /** 总开关；false 时巡检不注册 */
    private boolean enabled = false;

    /** 巡检间隔（毫秒）：多久查一次「到期的 PLATFORM 点位」 */
    private long sweepIntervalMs = 1000L;

    /** 单轮最多取多少个到期点位，防止一次加载过多 */
    private int batchLimit = 200;

    /** 单轮最多处理多少台设备 */
    private int enabledDeviceLimit = 50;

    /** 同时允许打开的 Modbus 连接数 */
    private int maxConnections = 8;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 3000;

    /** 读超时（毫秒） */
    private int readTimeoutMs = 3000;

    /** 单次请求重试次数（modbus4j setRetries） */
    private int retry = 1;

    /**
     * 采集周期下限（毫秒）。
     * <p>点表可能被误配成 100ms，直接按配置轮询会打爆现场总线；到期查询会把
     * {@code read_interval_ms} 抬到该下限后再判定。
     */
    private int minIntervalMs = 1000;

    /** 采集专用线程池核心线程数 */
    private int corePoolSize = 2;

    /** 采集专用线程池最大线程数 */
    private int maxPoolSize = 4;

    /** 采集专用线程池队列容量 */
    private int queueCapacity = 200;

    /** driver_config 未提供 host 时的默认值；空表示必须由产品配置显式提供 */
    private String defaultHost = "";

    /** driver_config 未提供 port 时的默认值 */
    private int defaultPort = 502;

    /** driver_config 未提供 unitId 时的默认值 */
    private int defaultUnitId = 1;

    /** 有效采集周期：空值回落 60s，并抬到 minIntervalMs 下限。 */
    public int effectiveIntervalMs(Integer configured) {
        int interval = configured == null ? 60_000 : configured;
        return Math.max(interval, Math.max(1, minIntervalMs));
    }
}
