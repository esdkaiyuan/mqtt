package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EMQX 管理面接入配置。
 * <p>
 * 凭据与 {@code scripts/emqx-init.sh} 登录所用一致：先用 Dashboard 账号换取 Bearer token，
 * 后续请求统一携带（EMQX 5 的 REST API 不接受 Basic 认证）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.emqx")
public class EmqxProperties {

    /** API 根地址，形如 {@code http://emqx:18083/api/v5}（容器网络内直连，不经网关） */
    private String apiBaseUrl = "http://emqx:18083/api/v5";

    /** Dashboard 账号，与 compose 中 EMQX_DASHBOARD_USER 一致 */
    private String dashboardUser = "admin";

    /** Dashboard 密码，与 compose 中 EMQX_DASHBOARD_PASSWORD 一致 */
    private String dashboardPassword = "public";

    /** 禁用 / 停用时是否调用踢线接口立即断开已连接会话；false 时仅依赖授权缓存 TTL 收敛 */
    private boolean kickEnabled = true;
}