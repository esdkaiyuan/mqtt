package com.mqtt.cloud.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MQTT 连接配置
 * 对应 application.yml 中的 spring.mqtt.* 配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.mqtt")
public class MqttProperties {

    /** Broker 地址，如 tcp://localhost:1883 */
    private String host = "tcp://localhost:1883";

    private String username = "admin";

    private String password = "public";

    /** 客户端 ID 前缀，实际连接时会追加随机后缀避免冲突 */
    private String clientId = "mqtt_backend";

    /** 连接超时（秒） */
    private int timeout = 10;

    /** 心跳间隔（秒） */
    private int keepalive = 60;

    /** 是否自动重连，false 时首次连接失败即放弃（仅用于排障） */
    private boolean reconnect = true;

    /** 重连退避初始延迟（毫秒） */
    private long reconnectInitialDelayMs = 1000;

    /** 重连退避最大延迟（毫秒） */
    private long reconnectMaxDelayMs = 30000;

    /** 已连接状态下的巡检间隔（毫秒），用于兜底发现未被回调通知的断连 */
    private long connectedProbeIntervalMs = 30000;

    /** 是否清理会话，true 时重连后需要重新订阅 */
    private boolean cleanSession = true;

    /** 最大未确认消息数 */
    private int maxInflight = 100;
}