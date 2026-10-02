package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 告警中心配置（T-17 设计文档 §12）。
 * <p>
 * 前缀 {@code app.alert}，字段全部带默认值，风格对齐 {@link ShadowProperties} / {@link CommandProperties}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.alert")
public class AlertProperties {

    /** 告警评估总开关；置 false 时不再评估，离线巡检也不注册（已有活动告警保持原状） */
    private boolean enabled = true;

    /** 触发通知开关；置 false 时仍落库但不投递 {@code alert.triggered} */
    private boolean notifyEnabled = true;

    /** 恢复通知开关；置 false 时仍置 RECOVERED 但不投递 {@code alert.recovered} */
    private boolean recoverNotifyEnabled = true;

    /** 全局默认抑制窗口（秒）；规则未单独配置（{@code suppress_window_seconds <= 0}）时使用 */
    private int suppressWindowSeconds = 300;

    /** 离线巡检间隔（毫秒），{@code <= 0} 关闭 */
    private long sweepIntervalMs = 30000;

    /** 单轮巡检规则 / 记录处理上限，避免全表扫描 */
    private int sweepBatchSize = 200;
}
