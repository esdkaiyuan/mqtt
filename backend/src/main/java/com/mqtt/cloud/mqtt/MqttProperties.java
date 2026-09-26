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

    /** 是否自动重连 */
    private boolean reconnect = true;

    /** 是否清理会话，true 时重连后需要重新订阅 */
    private boolean cleanSession = true;

    /** 最大未确认消息数 */
    private int maxInflight = 100;
}