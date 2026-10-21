package com.mqtt.cloud.config;

import com.mqtt.cloud.service.OtaUpgradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * OTA 升级巡检（T-22 设计文档 §7.3，T-32 增灰度推进）。
 * <p>
 * 单轮巡检三件事：补投仍在 {@code PENDING} 且设备在线的记录；推进到期的灰度批次（G-11-1）；
 * 将超过 {@code app.ota.task-timeout-ms} 未回传的记录置 {@code TIMEOUT}。三者结束都会
 * 刷新受影响任务的计数与状态。
 * <p>
 * 顺序刻意是「补投 → 推进 → 超时」：先把上一批遗留的未下发记录补掉，再开下一批，
 * 最后才判超时。若把超时放前面，会把「刚下发、设备还没来得及回传」的记录误判为超时。
 * <p>
 * 形状对齐 {@link PropertyHistorySweeper}：{@code enabled=false} 或间隔非正时不注册（启动期
 * {@code log.info} 说明），{@code sweep()} 整体 try/catch 只记 WARN，绝不冒泡 —— 否则会中断
 * 调度线程，导致后续周期不再执行。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OtaDispatchSweeper implements SchedulingConfigurer {

    private final OtaUpgradeService otaUpgradeService;
    private final OtaProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getDispatchIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("OTA 升级巡检已关闭（enabled={}, dispatch-interval-ms={}）",
                    properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("OTA 升级巡检已启用：间隔 {} ms，灰度自动推进={}",
                interval, properties.getBatchIntervalMs() > 0);
    }

    private void sweep() {
        try {
            int dispatched = otaUpgradeService.sweepPending(properties.getDispatchBatchSize());
            int advanced = otaUpgradeService.sweepBatchAdvance(
                    properties.getBatchIntervalMs(), properties.getBatchAdvanceBatchSize());
            int timedOut = otaUpgradeService.sweepTimeout(properties.getTaskTimeoutMs());
            if (dispatched > 0 || advanced > 0 || timedOut > 0) {
                log.info("OTA 升级巡检完成：补投 {} 台，灰度推进 {} 台，超时 {} 台",
                        dispatched, advanced, timedOut);
            }
        } catch (Exception e) {
            log.warn("OTA 升级巡检异常", e);
        }
    }
}
