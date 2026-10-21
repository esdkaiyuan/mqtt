package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备调试中心配置（T-37）。
 * <p>
 * 前缀 {@code app.debug}。Broker 对外地址与端口在这里单独配置，而不是复用
 * {@code spring.mqtt.host}：后者在容器内是 {@code tcp://emqx:1883}，对浏览器 / 上位机不可达。
 * 任何字段都不包含平台账号或设备密钥。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.debug")
public class DebugProperties {

    /** 是否注册调试端点；关闭时 /devices/*&#47;debug/** 返回 404 */
    private boolean enabled = true;

    /** Broker 对外主机名或 IP（不带协议与端口），用于拼接连接参数 */
    private String publicBrokerHost = "localhost";

    /** MQTT over TCP 端口 */
    private int tcpPort = 1883;

    /** MQTT over TLS 端口 */
    private int tlsPort = 8883;

    /** MQTT over WebSocket 端口 */
    private int wsPort = 8083;

    /** WebSocket 监听路径 */
    private String wsPath = "/mqtt";

    /** 单次模拟上报载荷上限（字节） */
    private int maxPayloadBytes = 65536;
}
