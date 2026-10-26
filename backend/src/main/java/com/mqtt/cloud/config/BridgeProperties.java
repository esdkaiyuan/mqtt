package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据桥接配置（T-56）。
 * <p>
 * 前缀 {@code app.bridge}，当前承载 Kafka 出站配置；规则与场景的
 * {@code FORWARD_KAFKA} 动作共用同一份配置。
 * <p>
 * {@code kafka.enabled=false}（默认）时转发器不创建 Producer，动作直接失败，
 * 与 {@code app.rule.mqtt.enabled} 的降级语义一致。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.bridge")
public class BridgeProperties {

    private Kafka kafka = new Kafka();

    @Data
    public static class Kafka {

        /** Kafka 桥接总开关；false 时 FORWARD_KAFKA 动作直接失败 */
        private boolean enabled = false;

        /** Kafka 地址，{@code host:port} 逗号分隔；enabled=true 时必填 */
        private String bootstrapServers = "";

        /** 缺省 Topic；动作未配置 topic 时使用 */
        private String topic = "";

        /** 出站客户端 ID 前缀 */
        private String clientId = "mqtt-cloud-bridge";

        /** acks 配置：0 / 1 / all */
        private String acks = "all";

        /** 发送阻塞超时（毫秒），超时按失败处理并走动作重试 */
        private int maxBlockMs = 5000;

        /** 安全协议：PLAINTEXT / SASL_PLAINTEXT / SSL / SASL_SSL */
        private String securityProtocol = "PLAINTEXT";

        /** SASL 机制（如 PLAIN / SCRAM-SHA-256）；SASL 协议时必填 */
        private String saslMechanism = "";

        private String saslUsername = "";

        private String saslPassword = "";
    }
}