package com.mqtt.cloud.filter;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 开放接口限流过滤器（T-30 · E4）。
 * <p>
 * 兑现前端 {@code apiEndpoints.js} 早已对外宣称的「429 超出密钥的调用频率限制」——
 * 该文案在本次改造前无任何后端实现，属对外合规风险。
 * <p>
 * <b>算法</b>：固定窗口 + Redis {@code INCR}。固定窗口在窗口边界允许 2 倍突发，
 * 对开放接口足够；相比滑动窗口省去 Lua 脚本与多次往返，计数原子性由 Redis 保证。
 * <p>
 * <b>失败策略</b>：Redis 不可用时按 {@code failOpen} 决定放行或拒绝（默认放行），
 * 避免缓存故障连带打死全部第三方调用。
 * <p>
 * <b>限流键</b>：直接对 {@code X-API-Key} 原文取摘要，不做二次校验。
 * 这样本过滤器无需依赖 {@link ApiKeyAuthFilter} 的执行顺序（无论谁先执行都能计数），
 * 也不会为一个 key 多查一次数据库；无效 key 会在后续认证阶段返 401/3501，
 * 攻击者用随机 key 刷也只会打在自己的桶里。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class ApiKeyRateLimitFilter extends OncePerRequestFilter {

    private static final String EXTERNAL_API_PREFIX = "/external/v1/";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        // 仅限开放接口；写操作与读操作同限流，不区分方法
        return !resolvePath(request).startsWith(EXTERNAL_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            // 无凭证请求不参与计数，交给认证链路返回 401
            filterChain.doFilter(request, response);
            return;
        }

        String key = properties.getKeyPrefix() + digest(apiKey) + ":" + currentWindow();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 首次计数时设置过期：窗口自动回收，无需定时清理
                redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
            }
            if (count != null && count > properties.getMaxRequestsPerWindow()) {
                log.warn("开放接口触发限流 key={} count={} limit={}",
                        digest(apiKey), count, properties.getMaxRequestsPerWindow());
                writeTooManyRequests(response);
                return;
            }
        } catch (Exception e) {
            if (!properties.isFailOpen()) {
                log.error("限流检查失败且 failOpen=false，拒绝请求", e);
                writeTooManyRequests(response);
                return;
            }
            log.warn("限流检查失败，降级放行 err={}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 限流键取 key 摘要而非原文。
     * <p>
     * 避免把凭证明文写进 Redis key（Redis key 可能被 {@code KEYS}、慢日志、
     * 运维导出等途径泄露），也避免超长 key。
     */
    private String digest(String apiKey) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走不到这里；真走到则退回整体哈希值的 hashCode
            return String.valueOf(apiKey.hashCode());
        }
    }

    /** 当前固定窗口标识：按时间桶取整，跨窗口自然切换。 */
    private long currentWindow() {
        long windowMs = Math.max(properties.getWindowSeconds(), 1) * 1000L;
        return System.currentTimeMillis() / windowMs;
    }

    private String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(ResultCode.RATE_LIMIT_EXCEEDED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 告知客户端何时可重试，便于调用方退避
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(properties.getWindowSeconds()));
        objectMapper.writeValue(response.getWriter(), Result.error(ResultCode.RATE_LIMIT_EXCEEDED));
    }
}
