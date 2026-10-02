package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备影子与离线补发配置（T-16 设计文档 §12）。
 * <p>
 * 风格对齐 {@link CommandProperties}：字段带默认值，可用环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.shadow")
public class ShadowProperties {

    /** 上线补发总开关；关闭后仅保留入队与巡检日志，不主动补发。 */
    private boolean resendEnabled = true;

    /** 补发重试上限；达到该次数仍未成功则置 {@code FAILED}。 */
    private int retryMaxAttempts = 5;

    /** 指数退避基数（毫秒）。 */
    private long retryBaseDelayMs = 5000;

    /** 指数退避封顶（毫秒）。 */
    private long retryMaxDelayMs = 300000;

    /** 退避重试巡检间隔（毫秒），{@code 0} 关闭。 */
    private long retrySweepIntervalMs = 30000;

    /** 单轮巡检 / 单设备补发批量上限。 */
    private int resendBatchSize = 200;

    /** 影子 CAS 冲突重试次数；耗尽后记 WARN 并放弃本次合并。 */
    private int casRetry = 3;

    /** {@code QUEUED} 过期时长（毫秒），{@code 0} 表示不过期。 */
    private long queueTtlMs = 0;
}
