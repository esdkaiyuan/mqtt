package com.mqtt.cloud.common.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计日志的请求信息提取（T-53）。
 * <p>
 * 只读取请求头与路径，<b>绝不读取请求体</b>：写操作请求体里可能含设备密钥、API Key、
 * 证书私钥等敏感信息，审计日志不应成为新的泄漏面。
 */
public final class AuditRequestInfo {

    private static final int IP_MAX_LENGTH = 64;
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private AuditRequestInfo() {
    }

    /**
     * 客户端 IP：优先 {@code X-Forwarded-For} 首个地址（Nginx 反代场景），
     * 其次 {@code X-Real-IP}，最后退回直连地址。
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return truncate(first, IP_MAX_LENGTH);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return truncate(realIp.trim(), IP_MAX_LENGTH);
        }
        return truncate(request.getRemoteAddr(), IP_MAX_LENGTH);
    }

    public static String userAgent(HttpServletRequest request) {
        return truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LENGTH);
    }

    /**
     * 去掉 context-path 的请求路径。
     * <p>
     * 项目以 {@code server.servlet.context-path=/api} 部署，审计里存 {@code /devices/1}
     * 比 {@code /api/devices/1} 更稳定——换前缀不改历史记录。
     */
    public static String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            String result = uri.substring(contextPath.length());
            return result.isEmpty() ? "/" : result;
        }
        return uri;
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}