package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.MfaCrypto;
import com.mqtt.cloud.config.MfaProperties;
import com.mqtt.cloud.dto.request.MfaVerifyRequest;
import com.mqtt.cloud.dto.response.LoginResponseDTO;
import com.mqtt.cloud.dto.response.MfaSetupResponse;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.mapper.UserMapper;
import com.mqtt.cloud.util.JwtUtil;
import com.mqtt.cloud.util.TotpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * {@link MfaServiceImpl} 单元测试（T-58）。
 */
class MfaServiceImplTest {

    private static final String SECRET = "test-jwt-secret-with-enough-length-32-bytes";
    private static final Long USER_ID = 1L;

    private UserMapper userMapper;
    private JwtUtil jwtUtil;
    private MfaCrypto mfaCrypto;
    private MfaProperties properties;
    private MfaServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        properties = new MfaProperties();
        jwtUtil = new JwtUtil(SECRET, 3600000L);
        mfaCrypto = new MfaCrypto(SECRET, properties);
        service = new MfaServiceImpl(userMapper, jwtUtil, mfaCrypto, properties);

        user = new User();
        user.setId(USER_ID);
        user.setUsername("admin");
        user.setStatus("ACTIVE");
        user.setRole("ADMIN");
        user.setMfaEnabled(0);
        user.setDeleted(0);

        given(userMapper.selectById(USER_ID)).willReturn(user);
        given(userMapper.updateById(any(User.class))).willReturn(1);
        given(userMapper.updateLastLogin(anyLong(), any())).willReturn(1);
    }

    @Test
    void begin_enable_disable_should_manage_secret_lifecycle() {
        MfaSetupResponse setup = service.beginEnrollment(USER_ID);
        assertThat(setup.getSecret()).hasSize(32);
        assertThat(user.getMfaSecret()).isNotNull();
        assertThat(user.getMfaEnabled()).isZero();

        String code = currentCode(setup.getSecret());
        service.enable(USER_ID, code);
        assertThat(user.getMfaEnabled()).isEqualTo(1);
        assertThat(user.getMfaConfirmedAt()).isNotNull();

        service.disable(USER_ID, code);
        assertThat(user.getMfaEnabled()).isZero();
        assertThat(user.getMfaSecret()).isNull();
    }

    @Test
    void enable_with_wrong_code_should_throw_6320() {
        MfaSetupResponse setup = service.beginEnrollment(USER_ID);

        assertThatThrownBy(() -> service.enable(USER_ID, farCode(setup.getSecret())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6320));
        assertThat(user.getMfaEnabled()).isZero();
    }

    @Test
    void verify_login_should_issue_access_token_for_valid_code() {
        MfaSetupResponse setup = service.beginEnrollment(USER_ID);
        String code = currentCode(setup.getSecret());
        service.enable(USER_ID, code);
        String mfaToken = jwtUtil.generateMfaToken(USER_ID, "admin", "ADMIN");

        LoginResponseDTO response = service.verifyLogin(verifyRequest(mfaToken, code));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getMfaRequired()).isFalse();
        assertThat(response.getUsername()).isEqualTo("admin");
    }

    @Test
    void verify_login_should_reject_wrong_code_and_invalid_token() {
        MfaSetupResponse setup = service.beginEnrollment(USER_ID);
        service.enable(USER_ID, currentCode(setup.getSecret()));
        String mfaToken = jwtUtil.generateMfaToken(USER_ID, "admin", "ADMIN");

        assertThatThrownBy(() -> service.verifyLogin(
                verifyRequest(mfaToken, farCode(setup.getSecret()))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6320));

        assertThatThrownBy(() -> service.verifyLogin(verifyRequest("bad-token", "123456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6321));
    }

    private MfaVerifyRequest verifyRequest(String mfaToken, String code) {
        MfaVerifyRequest request = new MfaVerifyRequest();
        request.setMfaToken(mfaToken);
        request.setCode(code);
        return request;
    }

    private String currentCode(String secret) {
        long counter = System.currentTimeMillis() / 1000L / 30L;
        return TotpUtil.codeAt(secret, counter, 6);
    }

    private String farCode(String secret) {
        long counter = System.currentTimeMillis() / 1000L / 30L + 1000L;
        return TotpUtil.codeAt(secret, counter, 6);
    }
}