package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自动化 / 场景联动配置（T-23 设计文档 §12）。
 * <p>
 * 前缀 {@code app.scene}，字段全部带默认值，风格对齐 {@link RuleProperties} / {@link AlertProperties}。
 * {@code app.scene.enabled} 默认 {@code true}：置 {@code false} 时不做触发评估、定时巡检不注册，
 * 既有规则 / 告警 / OTA 巡检不受影响。
 * <p>
 * 转发类步骤**不新增配置**：{@code FORWARD_HTTP} / {@code FORWARD_MQTT} 复用 T-19 的
 * {@code RuleHttpForwarder} / {@code RuleMqttForwarder}，出站超时、签名密钥、载荷大小上限、
 * 第三方 Broker 沿用 {@code app.rule.*}，避免同一份出站参数出现两处真相。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.scene")
public class SceneProperties {

    /** 场景引擎总开关；置 false 时不做触发评估、定时巡检不注册（既有规则 / 告警不受影响） */
    private boolean enabled = true;

    /** 单用户场景数上限 */
    private int maxScenesPerUser = 100;

    /** 单场景步骤数上限 */
    private int maxStepsPerScene = 20;

    /** 单场景条件项数上限 */
    private int maxConditionsPerScene = 10;

    /** 单步骤目标设备数上限（同时受 DeviceBatchService.MAX_BATCH_SIZE=500 约束） */
    private int maxTargetDevices = 500;

    /** 单步骤最大延时（秒） */
    private int maxStepDelaySeconds = 86400;

    /** 场景定义缓存 TTL（秒）；0 关闭缓存。保存只失效本副本，多副本收敛窗口 = 该 TTL */
    private int cacheTtlSeconds = 60;

    /** 步骤执行线程池核心线程数 */
    private int executorCoreSize = 2;

    /** 步骤执行线程池最大线程数 */
    private int executorMaxSize = 8;

    /** 步骤执行线程池队列容量 */
    private int executorQueueCapacity = 500;

    /** 步骤失败重试上限，达到该次数仍未成功则中止后续步骤 */
    private int retryMaxAttempts = 3;

    /** 指数退避基数（毫秒） */
    private long retryBaseDelayMs = 5000;

    /** 指数退避封顶（毫秒） */
    private long retryMaxDelayMs = 300000;

    /** 延时与重试巡检间隔（毫秒），0 关闭（同时关闭卡死恢复与保留清理）；决定延时精度 */
    private long sweepIntervalMs = 1000;

    /** 单轮巡检处理上限 */
    private int sweepBatchSize = 200;

    /** 单步执行超时（毫秒）；超过该时长仍为 RUNNING 的步骤由巡检复位重试 */
    private long stepTimeoutMs = 300000;

    /** 执行记录保留天数（仅清理终态记录），0 表示不清理 */
    private int executionRetentionDays = 30;

    /** 定时触发使用的时区 */
    private String timerZone = "Asia/Shanghai";
}