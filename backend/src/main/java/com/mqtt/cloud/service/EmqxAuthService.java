package com.mqtt.cloud.service;

/**
 * EMQX HTTP 认证回调（T-25 起承载三种认证方式）。
 * <p>
 * 回调在用户名、口令、客户端 ID 之外还接收客户端的证书主体字段：
 * EMQX 以占位符 {@code ${cert_subject}} 与 {@code ${cert_common_name}} 填充，
 * 非 TLS 连接下为空串。分派由产品 {@code auth_mode} 决定，
 * 失败一律返回拒入，不向 EMQX 暴露具体原因。
 */
public interface EmqxAuthService {

    /**
     * 认证一台客户端。
     *
     * @param username       设备用户名 {@code {productKey}.{deviceKey}}，或平台账号
     * @param password       设备密钥明文，或 JWT 模式下的设备令牌
     * @param clientId       EMQX 传入的客户端 ID
     * @param certSubject    客户端证书主体 DN，非 TLS 连接为空串
     * @param certCommonName 客户端证书主体 CN，非 TLS 连接为空串
     */
    boolean authenticate(String username, String password, String clientId,
                         String certSubject, String certCommonName);
}
