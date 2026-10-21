package com.mqtt.cloud.config;

import com.mqtt.cloud.service.ModbusCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * Modbus 采集巡检注册（T-31 P4）。
 * <p>
 * 形状对齐 {@link OtaDispatchSweeper}：{@code enabled=false} 或间隔非正不注册，
 * {@code sweep()} 整体 try/catch 只记 WARN，绝不冒泡到调度线程。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ModbusCollectSweeper implements SchedulingConfigurer {

    private final ModbusCollectService modbusCollectService;
    private final ModbusCollectProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        long interval = properties.getSweepIntervalMs();
        if (!properties.isEnabled() || interval <= 0) {
            log.info("Modbus 采集巡检已关闭（enabled={}, sweep-interval-ms={}）",
                    properties.isEnabled(), interval);
            return;
        }
        taskRegistrar.addFixedDelayTask(this::sweep, Duration.ofMillis(interval));
        log.info("Modbus 采集巡检已启用：间隔 {} ms，单轮点位上限 {}，设备上限 {}",
                interval, properties.getBatchLimit(), properties.getEnabledDeviceLimit());
    }

    private void sweep() {
        try {
            int scheduled = modbusCollectService.sweepDuePoints();
            if (scheduled > 0) {
                log.info("Modbus 采集巡检：本轮提交设备任务 {} 台", scheduled);
            }
        } catch (Exception e) {
            log.warn("Modbus 采集巡检异常", e);
        }
    }
}
