package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.MfaCrypto;
import com.mqtt.cloud.config.MfaProperties;
import com.mqtt.cloud.dto.request.MfaVerifyRequest;
import com.mqtt.cloud.dto.response.LoginResponseDTO;
import com.mqtt.cloud.dto.response.MfaSetupResponse;
import com.mqtt.cloud.dto.response.UserResponseDTO;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.mapper.UserMapper;
import com.mqtt.cloud.service.MfaService;
import com.mqtt.cloud.util.JwtUtil;
import com.mqtt.cloud.util.TotpUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * TOTP MFA 服务实现（T-58）。
 * <p>
 * 密钥生命周期：{@code beginEnrollment} 生成密文待确认（{@code mfa_enabled=0}），
 * {@code enable} 校验动态口令后置 {@code mfa_enabled=1}，{@code disable} 校验后清空密钥。
 * 二步登录使用单独的 MFA 临时令牌（{@code mfa=true}，5 分钟），校验通过才签发访问令牌。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final MfaCrypto mfaCrypto;
    private final MfaProperties properties;

    @Override
    public boolean isEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && Integer.valueOf(1).equals(user.getMfaEnabled());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MfaSetupResponse beginEnrollment(Long userId) {
        requireEnabled();
        User user = requireUser(userId);
        if (Integer.valueOf(1).equals(user.getMfaEnabled())) {
            throw new BusinessException(ResultCode.MFA_ALREADY_ENABLED);
        }
        String secret = TotpUtil.generateSecret(properties.getSecretLength());
        user.setMfaSecret(mfaCrypto.encrypt(secret));
        // 未确认前保持 mfa_enabled=0：绑定失败不留下「已启用却无法登录」的状态
        user.setMfaEnabled(0);
        user.setMfaConfirmedAt(null);
        userMapper.updateById(user);

        MfaSetupResponse response = new MfaSetupResponse();
        response.setSecret(secret);
        response.setIssuer(properties.getIssuer());
        response.setDigits(properties.getDigits());
        response.setPeriodSeconds(properties.getTimeStepSeconds());
        response.setOtpauthUri(TotpUtil.otpauthUri(properties.getIssuer(), user.getUsername(),
                secret, properties.getDigits(), properties.getTimeStepSeconds()));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long userId, String code) {
        requireEnabled();
        User user = requireUser(userId);
        if (Integer.valueOf(1).equals(user.getMfaEnabled())) {
            throw new BusinessException(ResultCode.MFA_ALREADY_ENABLED);
        }
        String secret = mfaCrypto.decrypt(user.getMfaSecret());
        if (secret == null) {
            throw new BusinessException(ResultCode.MFA_NOT_ENROLLED);
        }
        if (!verifyCode(secret, code)) {
            throw new BusinessException(ResultCode.MFA_CODE_INVALID);
        }
        user.setMfaEnabled(1);
        user.setMfaConfirmedAt(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户启用 MFA: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long userId, String code) {
        requireEnabled();
        User user = requireUser(userId);
        if (!Integer.valueOf(1).equals(user.getMfaEnabled())) {
            throw new BusinessException(ResultCode.MFA_NOT_ENROLLED);
        }
        String secret = mfaCrypto.decrypt(user.getMfaSecret());
        if (secret == null || !verifyCode(secret, code)) {
            throw new BusinessException(ResultCode.MFA_CODE_INVALID);
        }
        user.setMfaEnabled(0);
        user.setMfaSecret(null);
        user.setMfaConfirmedAt(null);
        userMapper.updateById(user);
        log.info("用户停用 MFA: userId={}", userId);
    }

    @Override
    public LoginResponseDTO verifyLogin(MfaVerifyRequest request) {
        requireEnabled();
        Claims claims;
        try {
            claims = jwtUtil.parse(request.getMfaToken());
        } catch (BusinessException e) {
            throw new BusinessException(ResultCode.MFA_TOKEN_INVALID);
        }
        if (!jwtUtil.isMfaToken(claims)) {
            throw new BusinessException(ResultCode.MFA_TOKEN_INVALID);
        }
        Long userId = jwtUtil.getUserId(claims);
        User user = requireUser(userId);
        if ("LOCKED".equals(user.getStatus()) || "DISABLED".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }
        if (!Integer.valueOf(1).equals(user.getMfaEnabled())) {
            throw new BusinessException(ResultCode.MFA_NOT_ENROLLED);
        }
        String secret = mfaCrypto.decrypt(user.getMfaSecret());
        if (secret == null || !verifyCode(secret, request.getCode())) {
            throw new BusinessException(ResultCode.MFA_CODE_INVALID);
        }
        // 动态口令通过才算登录完成，此时才更新最后登录时间
        userMapper.updateLastLogin(user.getId(), LocalDateTime.now());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return LoginResponseDTO.of(token, toResponse(user));
    }

    private boolean verifyCode(String secret, String code) {
        return TotpUtil.verify(secret, code, properties.getTimeStepSeconds(),
                properties.getAllowedWindow(), properties.getDigits());
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ResultCode.MFA_DISABLED);
        }
    }

    private User requireUser(Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return user;
    }

    private UserResponseDTO toResponse(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        return dto;
    }
}