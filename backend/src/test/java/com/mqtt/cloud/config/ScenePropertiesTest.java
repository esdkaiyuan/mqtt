package com.mqtt.cloud.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场景联动配置单测（T-23 设计文档 §12 / §14.1）。
 * <p>
 * 覆盖：{@code app.scene.*} 全部 18 个字段的默认值与设计文档一致；前缀绑定（kebab-case 松散绑定）
 * 可覆盖默认值，等价于验证 Lombok setter / getter 生效；前缀缺省时保持默认值。
 * <p>
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
class ScenePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfig.class);

    /**
     * 默认值应与设计文档 §12 一致。
     */
    @Test
    void defaults_should_match_design_doc() {
        SceneProperties properties = new SceneProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxScenesPerUser()).isEqualTo(100);
        assertThat(properties.getMaxStepsPerScene()).isEqualTo(20);
        assertThat(properties.getMaxConditionsPerScene()).isEqualTo(10);
        assertThat(properties.getMaxTargetDevices()).isEqualTo(500);
        assertThat(properties.getMaxStepDelaySeconds()).isEqualTo(86400);
        assertThat(properties.getCacheTtlSeconds()).isEqualTo(60);
        assertThat(properties.getExecutorCoreSize()).isEqualTo(2);
        assertThat(properties.getExecutorMaxSize()).isEqualTo(8);
        assertThat(properties.getExecutorQueueCapacity()).isEqualTo(500);
        assertThat(properties.getRetryMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetryBaseDelayMs()).isEqualTo(5000L);
        assertThat(properties.getRetryMaxDelayMs()).isEqualTo(300000L);
        assertThat(properties.getSweepIntervalMs()).isEqualTo(1000L);
        assertThat(properties.getSweepBatchSize()).isEqualTo(200);
        assertThat(properties.getStepTimeoutMs()).isEqualTo(300000L);
        assertThat(properties.getExecutionRetentionDays()).isEqualTo(30);
        assertThat(properties.getTimerZone()).isEqualTo("Asia/Shanghai");
    }

    /**
     * 前缀 {@code app.scene} 下的属性应能绑定并覆盖全部默认值（setter 绑定）。
     */
    @Test
    void binding_should_override_defaults_via_prefix() {
        contextRunner
                .withPropertyValues(
                        "app.scene.enabled=false",
                        "app.scene.max-scenes-per-user=5",
                        "app.scene.max-steps-per-scene=3",
                        "app.scene.max-conditions-per-scene=2",
                        "app.scene.max-target-devices=10",
                        "app.scene.max-step-delay-seconds=60",
                        "app.scene.cache-ttl-seconds=0",
                        "app.scene.executor-core-size=1",
                        "app.scene.executor-max-size=2",
                        "app.scene.executor-queue-capacity=10",
                        "app.scene.retry-max-attempts=1",
                        "app.scene.retry-base-delay-ms=100",
                        "app.scene.retry-max-delay-ms=200",
                        "app.scene.sweep-interval-ms=500",
                        "app.scene.sweep-batch-size=5",
                        "app.scene.step-timeout-ms=1000",
                        "app.scene.execution-retention-days=7",
                        "app.scene.timer-zone=UTC")
                .run(context -> {
                    assertThat(context).hasSingleBean(SceneProperties.class);
                    SceneProperties properties = context.getBean(SceneProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getMaxScenesPerUser()).isEqualTo(5);
                    assertThat(properties.getMaxStepsPerScene()).isEqualTo(3);
                    assertThat(properties.getMaxConditionsPerScene()).isEqualTo(2);
                    assertThat(properties.getMaxTargetDevices()).isEqualTo(10);
                    assertThat(properties.getMaxStepDelaySeconds()).isEqualTo(60);
                    assertThat(properties.getCacheTtlSeconds()).isZero();
                    assertThat(properties.getExecutorCoreSize()).isEqualTo(1);
                    assertThat(properties.getExecutorMaxSize()).isEqualTo(2);
                    assertThat(properties.getExecutorQueueCapacity()).isEqualTo(10);
                    assertThat(properties.getRetryMaxAttempts()).isEqualTo(1);
                    assertThat(properties.getRetryBaseDelayMs()).isEqualTo(100L);
                    assertThat(properties.getRetryMaxDelayMs()).isEqualTo(200L);
                    assertThat(properties.getSweepIntervalMs()).isEqualTo(500L);
                    assertThat(properties.getSweepBatchSize()).isEqualTo(5);
                    assertThat(properties.getStepTimeoutMs()).isEqualTo(1000L);
                    assertThat(properties.getExecutionRetentionDays()).isEqualTo(7);
                    assertThat(properties.getTimerZone()).isEqualTo("UTC");
                });
    }

    /**
     * 前缀缺省时应保持默认值。
     */
    @Test
    void binding_should_keep_defaults_when_prefix_absent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SceneProperties.class);
            SceneProperties properties = context.getBean(SceneProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getMaxScenesPerUser()).isEqualTo(100);
            assertThat(properties.getMaxStepsPerScene()).isEqualTo(20);
            assertThat(properties.getTimerZone()).isEqualTo("Asia/Shanghai");
        });
    }

    /**
     * 仅注册配置属性绑定，避免扫描 {@code @Component} 造成重复 Bean。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SceneProperties.class)
    static class BindingConfig {
    }
}
