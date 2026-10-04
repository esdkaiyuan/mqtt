package com.mqtt.cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务配置
 */
@Configuration
public class AsyncConfig {

    /**
     * Webhook 回调专用线程池
     * 回调失败不应影响 MQTT 消息处理主流程，因此异步执行。
     */
    @Bean("webhookExecutor")
    public ThreadPoolTaskExecutor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("webhook-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 规则动作执行专用线程池（T-19 设计文档 §4.6 / §8.4）。
     * <p>
     * 慢动作（出站 HTTP 超时、第三方 Broker 不可达）不得占用摄取 worker；有界队列在过载时
     * 直接拒绝投递（由评估侧把记录置 {@code FAILED}），避免任务无限堆积。
     */
    @Bean("ruleExecutor")
    public ThreadPoolTaskExecutor ruleExecutor(RuleProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorCoreSize());
        executor.setMaxPoolSize(properties.getExecutorMaxSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("rule-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 场景步骤执行专用线程池（T-23 设计文档 §4.5 / §8.4）。
     * <p>
     * 与 {@code ruleExecutor} 隔离：慢动作（延时排期、出站转发、多设备逐台下发）不得占用摄取 worker，
     * 也不得挤占规则动作线程；有界队列在过载时直接拒绝投递（由评估 / 巡检侧把步骤置 {@code FAILED}）。
     */
    @Bean("sceneExecutor")
    public ThreadPoolTaskExecutor sceneExecutor(SceneProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorCoreSize());
        executor.setMaxPoolSize(properties.getExecutorMaxSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("scene-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}