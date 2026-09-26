package com.mqtt.cloud.filter;

import com.mqtt.cloud.entity.ApiKey;
import com.mqtt.cloud.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Key 认证过滤器
 * <p>
 * 仅作用于 /external/v1/** 开放接口，校验 X-API-Key 请求头。
 * 校验通过后写入 EXTERNAL 角色，由 SecurityConfig 统一决定访问权限。
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String EXTERNAL_API_PREFIX = "/external/v1/";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !resolvePath(request).startsWith(EXTERNAL_API_PREFIX);
    }

    /**
     * 去掉 context-path 后得到应用内路径。
     * getRequestURI() 会包含 context-path（本项目为 /api），直接比较会永远不匹配。
     */
    private String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            ApiKey key = apiKeyService.validateApiKey(apiKey);
            if (key != null) {
                apiKeyService.updateLastUsed(key.getId());
                UserPrincipal principal = new UserPrincipal(key.getUserId(), "EXTERNAL", "EXTERNAL", key.getId());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}