package com.mqtt.cloud.service;

import java.time.LocalDateTime;

/**
 * 设备令牌服务（T-25）。
 * <p>
 * 为处于 {@code JWT} 认证方式的产品签发设备令牌，并在认证回调中校验令牌。
 * 令牌以独立的对称密钥签名，与控制台登录令牌的密钥完全隔离：
 * 一端泄露不会波及另一端，轮换一者也不必同步另一者。
 * 令牌主体固定为设备用户名 {@code {productKey}.{deviceKey}}，
 * 与 EMQX 传入的 {@code username} 逐字对应，避免设备冒用他人身份。
 */
public interface DeviceTokenService {

    /**
     * 为指定设备签发令牌。
     *
     * @param deviceId 设备主键
     * @return 令牌及有效期，明文令牌仅随本次响应返回
     * @throws com.mqtt.cloud.common.exception.BusinessException 设备不存在时抛 {@code DEVICE_NOT_FOUND}，
     *         开关未启用或产品未处于 JWT 模式时抛 {@code AUTH_MODE_UNSUPPORTED}，
     *         签发过程失败时抛 {@code DEVICE_TOKEN_ISSUE_FAILED}
     */
    DeviceToken issueToken(Long deviceId);

    /**
     * 校验令牌是否由本服务签发且归属于指定用户名。
     * <p>
     * 签名不符、已过期、主体不符或密钥不可用一并返回 {@code false}，
     * 调用方按认证失败处理，不区分原因。
     *
     * @param token    设备令牌
     * @param username 期望的设备用户名 {@code {productKey}.{deviceKey}}
     */
    boolean verify(String token, String username);

    /**
     * 令牌签发结果，字段与设计文档 §5.3 的响应体一致。
     */
    record DeviceToken(Long deviceId, String username, String token, LocalDateTime expiresAt) {
    }
}
