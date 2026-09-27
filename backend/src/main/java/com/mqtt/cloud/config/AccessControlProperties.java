package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}