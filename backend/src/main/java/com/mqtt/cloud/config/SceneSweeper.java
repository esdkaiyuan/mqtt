package com.mqtt.cloud.config;

import com.mqtt.cloud.service.SceneSweeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 场景步骤巡检注册（T-23 设计文档 §8.9）。
 * <p>
 * 与 {@link RuleSweeper} / {@link AlertSweeper} 同款：用 {@link SchedulingConfigurer} 表达
 * 「间隔为 0 或总开关关闭即不注册」；三个子任务各自 {@code try/catch}，任一异常都不中断调度线程。
 * 默认间隔 1000 ms，保证「延时 5s」的实际延时误差 &lt; 1s。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SceneSweeper implements SchedulingConfigurer {

    private final SceneSweeperService sceneSweeperService;
    private final SceneProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("场景步骤巡检已关闭（enabled={}, sweep-interval-ms={}）",
                    properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("场景步骤巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            int delivered = sceneSweeperService.sweepDueSteps();
            if (delivered > 0) {
                log.info("场景到期步骤投递完成：{} 条", delivered);
            }
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("场景到期步骤拾取异常", e);
        }
        try {
            int recovered = sceneSweeperService.recoverStaleSteps();
            if (recovered > 0) {
                log.info("场景卡死步骤恢复完成：{} 条", recovered);
            }
        } catch (Exception e) {
            log.warn("场景卡死步骤恢复异常", e);
        }
        try {
            int purged = sceneSweeperService.purgeExpired();
            if (purged > 0) {
                log.info("场景执行记录清理完成：删除 {} 条", purged);
            }
        } catch (Exception e) {
            log.warn("场景执行记录清理异常", e);
        }
    }
}