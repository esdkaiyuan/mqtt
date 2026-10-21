package com.mqtt.cloud.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Modbus 采集专用线程池（T-31 P4）。
 * <p>
 * 不复用 {@link ScheduleConfig} 的 poolSize=3 巡检池：Modbus 读取是阻塞 IO，
 * 一台设备超时就会占住巡检线程，连带拖慢告警 / OTA / 规则等所有巡检。
 * <p>
 * 拒绝策略为 CallerRuns：过载时让调度线程自己执行，表现为降速而不是静默丢弃采集任务。
 */
@Slf4j
@Configuration
public class ModbusCollectExecutorConfig {

    public static final String EXECUTOR_NAME = "modbusCollectExecutor";

    @Bean(name = EXECUTOR_NAME)
    public ThreadPoolTaskExecutor modbusCollectExecutor(ModbusCollectProperties properties) {
        int core = Math.max(1, properties.getCorePoolSize());
        int max = Math.max(core, properties.getMaxPoolSize());
        int queue = Math.max(1, properties.getQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("modbus-collect-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.warn("Modbus 采集线程池已满，改由调度线程执行: poolSize={}, queue={}",
                    ((ThreadPoolExecutor) pool).getPoolSize(), ((ThreadPoolExecutor) pool).getQueue().size());
            task.run();
        });
        executor.initialize();
        return executor;
    }
}
