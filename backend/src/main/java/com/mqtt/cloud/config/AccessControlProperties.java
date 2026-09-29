package com.mqtt.cloud.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.access-control")
public class AccessControlProperties {

    /** 是否真正校验认证与授权；false 时回调一律放行（迁移期） */
    private boolean enforceAuth = true;

    /** 内部接口共享令牌 */
    private String internalToken;

    /** 平台账号密码 */
    private String platformSecret;

    /** 迁移期前端受限账号密码 */
    private String frontendSecret;

    /**
     * 迁移期双轨运行会放宽认证与授权，必须在启动日志中显式提示，避免长期遗忘。
     */
    @PostConstruct
    void warnOnMigrationMode() {
        if (!enforceAuth) {
            log.warn("接入访问控制处于迁移期：ACCESS_CONTROL_ENFORCE_AUTH=false，认证与授权回调一律放行。"
                    + "存量设备全部刷机后必须置为 true。");
        }
    }
}