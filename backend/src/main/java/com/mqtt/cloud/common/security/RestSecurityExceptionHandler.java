package com.mqtt.cloud.common.security;

import tools.jackson.databind.ObjectMapper;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全异常响应处理器
 * <p>
 * 过滤器链中抛出的认证/授权异常不会经过 ControllerAdvice，
 * 这里统一输出与业务接口一致的标准 Result 结构，避免前端拿到空响应体。
 */
@Component
@RequiredArgsConstructor
public class RestSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * 请求属性名：过滤器解析凭证失败时写入具体错误码（如 TOKEN_INVALID / API_KEY_INVALID），
     * 供此处输出精确的业务码，避免所有认证失败都退化成笼统的 401。
     */
    public static final String AUTH_ERROR_ATTRIBUTE = "com.mqtt.cloud.security.AUTH_ERROR";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, resolveAuthError(request));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ResultCode.FORBIDDEN);
    }

    private ResultCode resolveAuthError(HttpServletRequest request) {
        Object attribute = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        return attribute instanceof ResultCode resultCode ? resultCode : ResultCode.UNAUTHORIZED;
    }

    private void write(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Result.error(resultCode));
    }
}