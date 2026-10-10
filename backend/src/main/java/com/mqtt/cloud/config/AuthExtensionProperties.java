package com.mqtt.cloud.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证方式扩展配置（T-25）。
 * <p>承载 X.509 与 JWT 两条新增认证通路的开关与材料路径。
 * 两条通路默认关闭，关闭时产品 `auth_mode` 仅允许 `SECRET`，
 * 与存量部署行为一致。开启后密钥与 CA 私钥仅从本地文件读取，
 * 不入库、不落日志。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.auth-extension")
public class AuthExtensionProperties {

    /** X.509 证书认证配置 */
    private X509 x509 = new X509();

    /** JWT 令牌认证配置 */
    private Jwt jwt = new Jwt();

    /**
     * 启动时校验开关与材料的自洽性：开启但缺少必填材料属于误配置，
     * 此时仅告警不阻断启动，避免因配置疏漏导致整个接入链路不可用。
     */
    @PostConstruct
    void warnOnIncompleteConfig() {
        if (x509.isEnabled() && (isBlank(x509.getCaCertPath()) || isBlank(x509.getCaKeyPath()))) {
            log.warn("已启用 X.509 认证但未配置 CA 证书或私钥路径：AUTH_X509_CA_CERT / AUTH_X509_CA_KEY，"
                    + "证书签发接口将不可用。");
        }
        if (jwt.isEnabled() && isBlank(jwt.getSecret())) {
            log.warn("已启用 JWT 认证但未配置签名密钥：AUTH_JWT_SECRET，设备令牌签发接口将不可用。");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** X.509 证书认证配置 */
    @Data
    public static class X509 {

        /** 是否启用 X.509 认证；false 时产品不允许选择 X.509 模式 */
        private boolean enabled = false;

        /** CA 证书文件路径（PEM） */
        private String caCertPath;

        /** CA 私钥文件路径（PEM，须为 PKCS#8 未加密格式） */
        private String caKeyPath;

        /** 签发证书的有效期天数 */
        private int validityDays = 365;
    }

    /** JWT 令牌认证配置 */
    @Data
    public static class Jwt {

        /** 是否启用 JWT 认证；false 时产品不允许选择 JWT 模式 */
        private boolean enabled = false;

        /** 设备令牌签名密钥，独立于控制台登录令牌密钥 */
        private String secret;

        /** 设备令牌有效期（秒） */
        private long ttlSeconds = 3600;
    }
}
