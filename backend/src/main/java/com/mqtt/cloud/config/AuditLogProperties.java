package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审计日志配置（T-53）。
 * <p>
 * 前缀 {@code app.audit-log}，风格对齐 {@link RateLimitProperties}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.audit-log")
public class AuditLogProperties {

    /** 审计总开关；false 时拦截器不写库（查询与历史记录仍可用） */
    private boolean enabled = true;

    /** 保留天数；<=0 表示不清理（由运维自行归档） */
    private int retentionDays = 180;

    /** 清理巡检间隔（毫秒）；<=0 时不注册巡检 */
    private long sweepIntervalMs = 3600000L;

    /** 单批删除行数，避免大事务长时间锁表 */
    private int purgeBatchSize = 1000;
}