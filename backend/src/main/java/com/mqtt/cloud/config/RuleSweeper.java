package com.mqtt.cloud.config;

import com.mqtt.cloud.service.RuleSweeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 规则重试巡检注册（T-19 设计文档 §8.6）。
 * <p>
 * 与 {@link AlertSweeper} / {@code ShadowRetrySweeper} 同款：用 {@link SchedulingConfigurer} 表达
 * 「间隔为 0 或总开关关闭即不注册」；重试与保留清理各自 {@code try/catch}，
 * 任一异常都不中断调度线程。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RuleSweeper implements SchedulingConfigurer {

    private final RuleSweeperService ruleSweeperService;
    private final RuleProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("规则重试巡检已关闭（enabled={}, sweep-interval-ms={}）",
                    properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("规则重试巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            int replayed = ruleSweeperService.sweepRetries();
            if (replayed > 0) {
                log.info("规则重试巡检完成：重放 {} 条", replayed);
            }
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("规则重试巡检异常", e);
        }
        try {
            int purged = ruleSweeperService.purgeExpired();
            if (purged > 0) {
                log.info("规则执行记录清理完成：删除 {} 条", purged);
            }
        } catch (Exception e) {
            log.warn("规则执行记录清理异常", e);
        }
    }
}