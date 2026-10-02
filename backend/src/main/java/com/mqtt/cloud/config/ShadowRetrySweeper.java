package com.mqtt.cloud.config;

import com.mqtt.cloud.service.DeviceCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * 影子补发退避巡检注册（T-16 设计文档 §9.3）。
 * <p>
 * 上线补发依赖「设备上报 data/heartbeat」这一信号；若补发当次发布失败，需要一条与事件无关的周期兜底，
 * 按指数退避把到期 {@code QUEUED} 重新投递（次数耗尽置 {@code FAILED}）。
 * <p>
 * 与 {@link CommandTimeoutSweeper} 同款：用 {@link SchedulingConfigurer} 表达「间隔为 0 即关闭」，
 * 巡检异常整体捕获，不中断调度线程。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ShadowRetrySweeper implements SchedulingConfigurer {

    private final DeviceCommandService deviceCommandService;
    private final ShadowProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getRetrySweepIntervalMs();
        if (!properties.isResendEnabled() || interval <= 0) {
            log.info("影子补发巡检已关闭（resend-enabled={}, retry-sweep-interval-ms={}）",
                    properties.isResendEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("影子补发巡检已启用：间隔 {} ms", interval);
    }

    private void sweep() {
        try {
            int delivered = deviceCommandService.sweepQueuedRetries();
            if (delivered > 0) {
                log.info("影子补发巡检完成，补发成功 {} 条", delivered);
            }
        } catch (Exception e) {
            // 巡检异常不得中断调度线程，否则后续周期不再执行
            log.warn("影子补发巡检异常", e);
        }
    }
}
