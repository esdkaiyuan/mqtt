package com.mqtt.cloud.filter;

import com.mqtt.cloud.common.audit.AuditActionResolver;
import com.mqtt.cloud.common.audit.AuditRequestInfo;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.config.AuditLogProperties;
import com.mqtt.cloud.entity.AuditLog;
import com.mqtt.cloud.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 审计日志拦截器（T-53）。
 * <p>
 * <b>为什么用 HandlerInterceptor 而不是 Filter</b>：审计需要登录态（SecurityContext），
 * 而 Filter 在安全链中的顺序需要显式编排；拦截器在 DispatcherServlet 内执行，
 * 天然位于鉴权之后，且 {@code afterCompletion} 拿得到最终状态码与异常。
 * <p>
 * <b>只记录写操作</b>（POST / PUT / PATCH / DELETE），读请求不落库——读路径量大且审计价值低，
 * 全量记录会把审计表变成第二份访问日志。
 * <p>
 * <b>不读请求体</b>：写请求里可能含设备密钥、API Key、证书私钥，审计日志只记录路径与结果。
 * <p>
 * 登录 / 注册 / 登出由 {@code AuthController} 显式记录（登出会提前清空 SecurityContext，
 * 拦截器在 afterCompletion 时已拿不到用户身份）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final String START_ATTR = AuditLogInterceptor.class.getName() + ".start";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> SKIP_PATH_PREFIXES = Set.of(
            "/internal/", "/ingest/", "/actuator/", "/swagger-ui", "/v3/api-docs");
    private static final Set<String> SKIP_PATHS = Set.of(
            "/auth/login", "/auth/register", "/auth/logout");

    private final AuditLogService auditLogService;
    private final AuditLogProperties properties;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        request.setAttribute(START_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        if (!properties.isEnabled() || !(handler instanceof HandlerMethod)) {
            return;
        }
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return;
        }
        String path = AuditRequestInfo.servletPath(request);
        if (SKIP_PATHS.contains(path) || SKIP_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
            return;
        }

        try {
            AuditActionResolver.Resolved resolved =
                    AuditActionResolver.resolve(request.getMethod(), path);
            UserPrincipal principal = SecurityUtils.getPrincipalOrNull();
            AuditLog entry = new AuditLog();
            if (principal != null) {
                entry.setUserId(principal.getUserId());
                entry.setUsername(principal.getUsername());
                entry.setRole(principal.getRole());
                entry.setSource("EXTERNAL".equals(principal.getRole()) ? "OPEN_API" : "CONSOLE");
            } else {
                entry.setSource("ANONYMOUS");
            }
            entry.setHttpMethod(request.getMethod());
            entry.setPath(path);
            entry.setAction(resolved.action());
            entry.setResourceType(resolved.resourceType());
            entry.setResourceId(resolved.resourceId());
            entry.setStatusCode(response.getStatus());
            entry.setSuccess(ex == null && response.getStatus() < 400);
            entry.setErrorMessage(resolveError(ex, response.getStatus()));
            entry.setIp(AuditRequestInfo.clientIp(request));
            entry.setUserAgent(AuditRequestInfo.userAgent(request));
            entry.setDurationMs(elapsedMs(request));
            entry.setCreatedAt(LocalDateTime.now());
            auditLogService.record(entry);
        } catch (Exception e) {
            // 审计失败绝不能影响业务请求：只记 WARN，由运维从应用日志发现
            log.warn("审计日志写入失败 path={} method={} err={}",
                    path, request.getMethod(), e.getMessage());
        }
    }

    private String resolveError(Exception ex, int status) {
        if (ex != null) {
            String message = ex.getMessage();
            return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
        }
        return status >= 400 ? "HTTP " + status : null;
    }

    private long elapsedMs(HttpServletRequest request) {
        Object start = request.getAttribute(START_ATTR);
        if (!(start instanceof Long startNanos)) {
            return 0L;
        }
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}