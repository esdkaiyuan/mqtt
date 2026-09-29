package com.mqtt.cloud.filter;

import com.mqtt.cloud.config.AccessControlProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";

    private final AccessControlProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 注意：server.servlet.context-path=/api，getRequestURI() 含 context-path，
        // 必须使用不含 context-path 的 getServletPath()，否则该过滤器永不生效。
        return !request.getServletPath().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String expected = properties.getInternalToken();
        String actual = request.getHeader(HEADER);
        if (!StringUtils.hasText(expected) || !expected.equals(actual)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"result\":\"deny\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}