package com.mqtt.cloud.config;

import com.mqtt.cloud.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 审计日志保留清理巡检（T-53）。
 * <p>
 * 与 {@link AlertSweeper} / {@link com.mqtt.cloud.config.RuleSweeper} 同款：
 * 间隔 <=0 或总开关关闭时不注册，单轮异常只记 WARN，不中断调度线程。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuditLogSweeper implements SchedulingConfigurer {

    private final AuditLogService auditLogService;
    private final AuditLogProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("审计日志清理巡检已关闭（enabled={}, sweep-interval-ms={}）",
                    properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("审计日志清理巡检已启用：间隔 {} ms，保留 {} 天",
                interval, properties.getRetentionDays());
    }

    private void sweep() {
        try {
            int deleted = auditLogService.purgeExpired();
            if (deleted > 0) {
                log.info("审计日志清理完成：删除 {} 条", deleted);
            }
        } catch (Exception e) {
            log.warn("审计日志清理巡检异常", e);
        }
    }
}