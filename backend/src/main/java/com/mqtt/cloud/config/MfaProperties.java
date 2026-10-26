package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TOTP MFA 配置（T-58）。
 * <p>
 * 前缀 {@code app.mfa}。{@code enabled=false} 时 MFA 控制器不注册、登录不要求二步验证；
 * 已绑定用户的密钥保留，重新开启后自动恢复生效。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.mfa")
public class MfaProperties {

    /** MFA 总开关；false 时 MFA 端点 404、登录忽略 MFA */
    private boolean enabled = true;

    /** TOTP 时间步长（秒），标准 30 */
    private int timeStepSeconds = 30;

    /** 允许的前后时间步窗口，默认 1（容忍 ±30 秒时钟偏差） */
    private int allowedWindow = 1;

    /** 动态口令位数，标准 6 */
    private int digits = 6;

    /** 密钥长度（字节），标准 20 */
    private int secretLength = 20;

    /** 展示名（authenticator 中的发行方） */
    private String issuer = "MQTT云平台";

    /**
     * MFA 密钥加密密钥（Base64 或原文，至少 32 字节）。
     * <p>
     * 留空时由 {@code JWT_SECRET} 派生；注意轮换 {@code JWT_SECRET} 会导致已绑定密钥无法解密，
     * 需要用户重新绑定（见部署文档）。
     */
    private String encryptionKey;
}