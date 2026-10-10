package com.mqtt.cloud.config;

import com.mqtt.cloud.service.AlertEscalationSweeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 告警升级巡检注册（T-27 设计文档 §5.1 / §12）。
 * <p>
 * 与 {@link NotificationSweeper} / {@link AlertSweeper} 同款：用 {@link SchedulingConfigurer}
 * 表达「间隔 &lt;= 0 或总开关关闭即不注册」，巡检异常整体捕获，不中断调度线程。
 * <p>
 * 总开关 {@code app.notification.enabled=false} 时不注册：升级属于本链路新增能力，
 * 回滚后不得再从存量未确认告警发出升级通知。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlertEscalationSweeper implements SchedulingConfigurer {

    private final AlertEscalationSweeperService alertEscalationSweeperService;
    private final NotificationProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getEscalation().getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("告警升级巡检已关闭（enabled={}, sweep-interval-ms={}）", properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("告警升级巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            alertEscalationSweeperService.sweep();
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("告警升级巡检异常", e);
        }
    }
}