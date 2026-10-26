package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.MfaVerifyRequest;
import com.mqtt.cloud.dto.response.LoginResponseDTO;
import com.mqtt.cloud.dto.response.MfaSetupResponse;

/**
 * TOTP 多因素认证服务（T-58）。
 */
public interface MfaService {

    /** 当前用户是否已启用 MFA。 */
    boolean isEnabled(Long userId);

    /**
     * 发起 MFA 绑定：生成待确认密钥并返回 authenticator 录入信息。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 已启用（{@code 6318}）
     */
    MfaSetupResponse beginEnrollment(Long userId);

    /** 确认绑定：动态口令正确后启用 MFA；错误 {@code 6320}，未发起绑定 {@code 6319}。 */
    void enable(Long userId, String code);

    /** 停用 MFA：需再次校验动态口令；错误 {@code 6320}，未启用 {@code 6319}。 */
    void disable(Long userId, String code);

    /**
     * 二步登录：校验 MFA 临时令牌与动态口令，成功返回完整登录响应。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 令牌无效（{@code 6321}）、
     *                                                             口令错误（{@code 6320}）
     */
    LoginResponseDTO verifyLogin(MfaVerifyRequest request);
}