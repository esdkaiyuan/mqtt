package com.mqtt.cloud.config;

import com.mqtt.cloud.service.AlertSweeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 告警离线巡检注册（T-17 设计文档 §8.3）。
 * <p>
 * 设备离线告警采用「拉」模式：状态改写入口多（批量摄取、单条更新、超时巡检、LWT 直离线），
 * 逐处挂钩既侵入主链路又易漏；改为周期巡检后，新建规则对「已离线设备」同样生效。
 * <p>
 * 与 {@link ShadowRetrySweeper} / {@link CommandTimeoutSweeper} 同款：用
 * {@link SchedulingConfigurer} 表达「间隔为 0 或总开关关闭即不注册」，巡检异常整体捕获，
 * 不中断调度线程。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlertSweeper implements SchedulingConfigurer {

    private final AlertSweeperService alertSweeperService;
    private final AlertProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("告警离线巡检已关闭（enabled={}, sweep-interval-ms={}）",
                    properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("告警离线巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            alertSweeperService.sweep();
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("告警离线巡检异常", e);
        }
    }
}
