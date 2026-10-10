package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通知渠道扩展配置（T-27 设计文档 §12）。
 * <p>
 * 前缀 {@code app.notification}，字段全部带默认值，风格对齐 {@link AlertProperties} / {@link RuleProperties}。
 * {@code app.notification.enabled} 是本链路新增能力的总开关：置 false 时渠道与升级端点返 404、
 * 站内信不再新增、邮件与短信不投递、升级巡检不注册；站内信读取端点保留，
 * 且**既有 Webhook 通道照常投递** —— 该开关不得改变 T-17 已交付行为。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    /** 本链路新增能力总开关；false 时只停用站内信写入、邮件与短信投递、升级巡检，Webhook 通道不受影响 */
    private boolean enabled = true;

    /** 站内信通道开关 */
    private boolean inappEnabled = true;

    /** 邮件渠道开关 */
    private boolean emailEnabled = true;

    /** 短信渠道开关 */
    private boolean smsEnabled = true;

    /** 单用户渠道数上限 */
    private int maxChannelsPerUser = 20;

    /** 单用户升级策略数上限 */
    private int maxEscalationPoliciesPerUser = 20;

    /** 单条告警最大升级级数，达到后不再升级 */
    private int maxEscalationLevel = 3;

    /** 单用户站内信保留上限，超出时清理最旧的已读条目；0 表示不清理 */
    private int inappRetentionPerUser = 500;

    /** 投递重试与留痕 */
    private Delivery delivery = new Delivery();

    /** 告警升级巡检 */
    private Escalation escalation = new Escalation();

    @Data
    public static class Delivery {

        /** 投递最大尝试次数，达到后置终态 FAILED 并停止重试 */
        private int maxAttempts = 3;

        /** 指数退避基数（秒），第 n 次重试顺延 backoffSeconds × 2^(n-1) */
        private int backoffSeconds = 60;

        /** 投递巡检间隔（毫秒），<=0 关闭（同时关闭保留清理） */
        private long sweepIntervalMs = 60000;

        /** 单轮巡检处理上限 */
        private int batchSize = 200;

        /** 投递记录保留天数，0 表示不清理 */
        private int retentionDays = 30;

        /** 投递线程池核心线程数 */
        private int executorCoreSize = 2;

        /** 投递线程池最大线程数 */
        private int executorMaxSize = 8;

        /** 投递线程池队列容量 */
        private int executorQueueCapacity = 500;

        /** 单渠道投递超时（毫秒） */
        private int timeoutMs = 5000;
    }

    @Data
    public static class Escalation {

        /** 升级巡检间隔（毫秒），<=0 关闭 */
        private long sweepIntervalMs = 60000;

        /** 单轮巡检处理上限 */
        private int batchSize = 200;
    }
}