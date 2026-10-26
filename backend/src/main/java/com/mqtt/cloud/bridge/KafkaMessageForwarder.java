package com.mqtt.cloud.bridge;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.BridgeProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka 数据桥接转发器（T-56）。
 * <p>
 * 规则 / 场景的 {@code FORWARD_KAFKA} 动作共用本组件：把渲染后的 JSON 载荷出站到第三方 Kafka。
 * 与 {@code RuleMqttForwarder} 同属「纯出站、不订阅」思路，但 Kafka Producer 自身具备
 * 批量 / 重试 / 元数据缓存能力，无需额外监督线程。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>Producer 懒创建、单例复用；配置变更需重启应用（与外部 MQTT 转发一致）；</li>
 *   <li>发送使用 {@code get(timeout)} 同步等待 Kafka 确认，失败按动作重试兜底；</li>
 *   <li>{@code app.bridge.kafka.enabled=false}（默认）不创建 Producer，动作直接失败；</li>
 *   <li>只依赖 {@code kafka-clients}，不引入 spring-kafka 自动配置。</li>
 * </ul>
 */
@Slf4j
@Component
public class KafkaMessageForwarder {

    private final BridgeProperties properties;
    private final Object lock = new Object();
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    private volatile KafkaProducer<String, String> producer;
    private volatile boolean shuttingDown = false;

    public KafkaMessageForwarder(BridgeProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getKafka().isEnabled();
    }

    /**
     * 发布一条桥接消息。
     *
     * @param topic  目标 Topic；为空时回落 {@code app.bridge.kafka.topic}
     * @param key    分区键，可为空
     * @param payload 载荷（JSON 字符串）
     * @throws BusinessException 未启用 / 配置缺失 / 发送失败（错误码 {@code 6323}）
     */
    public void publish(String topic, String key, String payload) {
        BridgeProperties.Kafka config = properties.getKafka();
        if (!config.isEnabled()) {
            throw new BusinessException(ResultCode.DATA_BRIDGE_FAILED, "Kafka 桥接未启用");
        }
        String effectiveTopic = (topic == null || topic.isBlank()) ? config.getTopic() : topic.trim();
        if (effectiveTopic.isBlank()) {
            throw new BusinessException(ResultCode.DATA_BRIDGE_FAILED, "Kafka 桥接未配置 Topic");
        }
        if (config.getBootstrapServers() == null || config.getBootstrapServers().isBlank()) {
            throw new BusinessException(ResultCode.DATA_BRIDGE_FAILED, "Kafka 桥接未配置 bootstrap-servers");
        }
        if (payload == null) {
            throw new BusinessException(ResultCode.DATA_BRIDGE_FAILED, "Kafka 载荷不能为空");
        }

        KafkaProducer<String, String> current = ensureProducer();
        try {
            current.send(new ProducerRecord<>(effectiveTopic, key, payload))
                    .get(Math.max(config.getMaxBlockMs(), 1), TimeUnit.MILLISECONDS);
            log.debug("Kafka 桥接已发送: topic={}, key={}", effectiveTopic, key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.DATA_BRIDGE_FAILED, "Kafka 发送被中断");
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            throw new BusinessException(ResultCode.DATA_BRIDGE_FAILED,
                    "Kafka 发送失败：" + e.getMessage());
        }
    }

    private KafkaProducer<String, String> ensureProducer() {
        synchronized (lock) {
            if (shuttingDown) {
                throw new BusinessException(ResultCode.DATA_BRIDGE_FAILED, "Kafka 桥接已关闭");
            }
            if (producer == null) {
                producer = newProducer(buildProperties());
            }
            return producer;
        }
    }

    /** Producer 实例化入口，便于单元测试注入替身。 */
    protected KafkaProducer<String, String> newProducer(Properties properties) {
        return new KafkaProducer<>(properties);
    }

    private Properties buildProperties() {
        BridgeProperties.Kafka config = properties.getKafka();
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getBootstrapServers().trim());
        props.put("client.id", config.getClientId() + "_" + instanceId);
        props.put("acks", config.getAcks());
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("max.block.ms", String.valueOf(Math.max(config.getMaxBlockMs(), 1)));
        // 轻微攒批与有限重试：桥接是旁路，重试语义由动作执行器统一负责
        props.put("linger.ms", "5");
        props.put("retries", "3");
        props.put("delivery.timeout.ms", "10000");
        props.put("security.protocol", config.getSecurityProtocol());
        if (config.getSaslMechanism() != null && !config.getSaslMechanism().isBlank()) {
            String mechanism = config.getSaslMechanism().trim();
            String module = mechanism.toUpperCase(java.util.Locale.ROOT).startsWith("SCRAM")
                    ? "org.apache.kafka.common.security.scram.ScramLoginModule required"
                    : "org.apache.kafka.common.security.plain.PlainLoginModule required";
            props.put("sasl.mechanism", mechanism);
            props.put("sasl.jaas.config", module + " username=\"" + config.getSaslUsername()
                    + "\" password=\"" + config.getSaslPassword() + "\";");
        }
        return props;
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            shuttingDown = true;
            if (producer == null) {
                return;
            }
            try {
                producer.close();
                log.info("Kafka 桥接 Producer 已关闭");
            } catch (Exception e) {
                log.warn("Kafka 桥接 Producer 关闭异常: {}", e.getMessage());
            } finally {
                producer = null;
            }
        }
    }
}