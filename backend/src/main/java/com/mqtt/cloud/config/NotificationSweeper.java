package com.mqtt.cloud.config;

import com.mqtt.cloud.service.NotificationSweeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 通知投递巡检注册（T-27 设计文档 §8.2 / §12）。
 * <p>
 * 与 {@link AlertSweeper} / {@link SceneTimerSweeper} 同款：用 {@link SchedulingConfigurer} 表达
 * 「间隔 &lt;= 0 或总开关关闭即不注册」，巡检异常整体捕获，不中断调度线程。
 * <p>
 * 总开关 {@code app.notification.enabled=false} 时不注册：该开关承诺「邮件与短信不投递」，
 * 而重投同样是一次真实投递，必须一并停用，否则回滚后仍会从存量失败记录里发出通知。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationSweeper implements SchedulingConfigurer {

    private final NotificationSweeperService notificationSweeperService;
    private final NotificationProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getDelivery().getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("通知投递巡检已关闭（enabled={}, sweep-interval-ms={}）", properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("通知投递巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            notificationSweeperService.sweep();
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("通知投递巡检异常", e);
        }
    }
}