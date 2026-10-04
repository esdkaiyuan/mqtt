package com.mqtt.cloud.config;

import com.mqtt.cloud.service.SceneTimerSweeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 场景定时触发巡检注册（T-23 设计文档 §8.4）。
 * <p>
 * 与 {@link SceneSweeper} 分离：本巡检只扫描启用中的 {@code TIMER} 场景并触发，间隔复用
 * {@code app.scene.sweep-interval-ms}；{@code enabled=false} 或间隔 {@code <= 0} 时不注册。
 * 单轮异常自行兜底，**不得中断调度线程**。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SceneTimerSweeper implements SchedulingConfigurer {

    private final SceneTimerSweeperService sceneTimerSweeperService;
    private final SceneProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("场景定时触发巡检已关闭（enabled={}, sweep-interval-ms={}）",
                    properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("场景定时触发巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            int fired = sceneTimerSweeperService.sweepTimers();
            if (fired > 0) {
                log.info("场景定时触发完成：{} 条", fired);
            }
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("场景定时触发巡检异常", e);
        }
    }
}