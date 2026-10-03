package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 消息规则 / 规则引擎配置（T-19 设计文档 §12）。
 * <p>
 * 前缀 {@code app.rule}，字段全部带默认值，风格对齐 {@link AlertProperties} / {@link ShadowProperties}。
 * {@code app.rule.mqtt.enabled} 默认 {@code false}：外部 MQTT 转发需部署方明确提供第三方 Broker，
 * 未配置时不应尝试连接（连接失败会产生持续告警噪声）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rule")
public class RuleProperties {

    /** 规则引擎总开关；置 false 时不做条件判定（既有告警与 Webhook 不受影响） */
    private boolean enabled = true;

    /** 单用户规则数上限 */
    private int maxRulesPerUser = 200;

    /** 规则定义缓存 TTL（秒）；0 关闭缓存。保存只失效本副本，多副本收敛窗口 = 该 TTL */
    private int cacheTtlSeconds = 60;

    /** 动作执行线程池核心线程数 */
    private int executorCoreSize = 2;

    /** 动作执行线程池最大线程数 */
    private int executorMaxSize = 8;

    /** 动作执行线程池队列容量 */
    private int executorQueueCapacity = 500;

    /** 失败重试上限，达到该次数仍未成功则置 FAILED */
    private int retryMaxAttempts = 3;

    /** 指数退避基数（毫秒） */
    private long retryBaseDelayMs = 5000;

    /** 指数退避封顶（毫秒） */
    private long retryMaxDelayMs = 300000;

    /** 重试巡检间隔（毫秒），0 关闭（同时关闭保留清理） */
    private long sweepIntervalMs = 30000;

    /** 单轮巡检处理上限 */
    private int sweepBatchSize = 200;

    /** 执行记录保留天数（仅清理终态记录），0 表示不清理 */
    private int executionRetentionDays = 30;

    /** 出站 HTTP 超时（毫秒） */
    private int httpTimeoutMs = 5000;

    /** 转发 HTTP 的 HMAC 签名密钥；为空时不下发 X-Signature 头 */
    private String httpSecret = "";

    /** 动作配置 / 载荷模板大小上限（字节） */
    private int maxPayloadBytes = 16384;

    /** 外部 MQTT 转发配置（出站到第三方 Broker，不经自有 EMQX） */
    private Mqtt mqtt = new Mqtt();

    @Data
    public static class Mqtt {

        /** 置 false 时 FORWARD_MQTT 动作直接置 FAILED，其余动作不受影响 */
        private boolean enabled = false;

        /** 第三方 Broker 地址（tcp:// 或 ssl://）；enabled=true 时必填 */
        private String brokerUrl = "";

        /** 出站客户端 ID */
        private String clientId = "mqtt-rule-forwarder";

        /** 第三方 Broker 用户名 */
        private String username = "";

        /** 第三方 Broker 密码 */
        private String password = "";

        /** 缺省 QoS，规则未配置时使用 */
        private int qos = 1;

        /** 保活间隔（秒） */
        private int keepalive = 60;

        /** 连接超时（秒） */
        private int connectTimeout = 10;
    }
}