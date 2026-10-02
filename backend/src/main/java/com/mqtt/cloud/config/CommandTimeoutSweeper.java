package com.mqtt.cloud.config;

import com.mqtt.cloud.service.DeviceCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 命令超时巡检注册（T-15 设计文档 §8）。
 * <p>
 * 用 {@link SchedulingConfigurer} 而非 {@code @Scheduled}：注解无法表达「间隔为 0 即关闭」——
 * {@code fixedDelay=0} 会退化成占满调度线程的忙循环，故此处按配置决定是否注册任务。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CommandTimeoutSweeper implements SchedulingConfigurer {

    private final DeviceCommandService deviceCommandService;
    private final CommandProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getTimeoutSweepIntervalMs();
        if (interval <= 0) {
            log.info("命令超时巡检已关闭（app.command.timeout-sweep-interval-ms={}）", interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("命令超时巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            int swept = deviceCommandService.sweepTimeouts();
            if (swept > 0) {
                log.info("命令超时巡检完成，置 TIMEOUT {} 条", swept);
            }
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("命令超时巡检异常", e);
        }
    }
}