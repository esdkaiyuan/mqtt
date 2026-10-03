package com.mqtt.cloud.config;

import com.mqtt.cloud.service.PropertyHistoryRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 属性历史保留清理巡检注册（T-21 设计文档 §5.4 / §9.2）。
 * <p>
 * 与 {@link RuleSweeper} / {@link AlertSweeper} 同款：用 {@link SchedulingConfigurer} 表达
 * 「间隔为 0 或清理开关关闭即不注册」；清理异常 {@code try/catch} 隔离，不中断调度线程。
 * 只有一个子步骤（保留清理），故单次 {@code try/catch} 即可。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PropertyHistorySweeper implements SchedulingConfigurer {

    private final PropertyHistoryRetentionService retentionService;
    private final PropertyHistoryProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getCleanupIntervalMs();
        if (!properties.isCleanupEnabled() || interval <= 0) {
            log.info("属性历史清理巡检已关闭（cleanup-enabled={}, cleanup-interval-ms={}）",
                    properties.isCleanupEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("属性历史清理巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            int purged = retentionService.purgeExpired();
            if (purged > 0) {
                log.info("属性历史清理完成：删除 {} 条", purged);
            }
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("属性历史清理巡检异常", e);
        }
    }
}
