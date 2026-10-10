package com.mqtt.cloud.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * traceId注入过滤器（T-30 · E1）。
 * <p>
 * 改造前全仓库无 MDC、无 traceId，客户报障时无法把一次请求的日志串起来。
 * 本过滤器为每个 HTTP 请求分配 traceId 并放入 {@link MDC}，
 * {@code logback-spring.xml} 的 pattern通过 {@code %X{traceId}} 输出。
 * <p>
 * <b>三种来源优先级</b>：
 * <ol>
 *   <li>上游传入的 {@code X-Trace-Id} —— 微服务 / 网关链路延续；</li>
 *   <li>设备上报的 deviceKey —— 便于按设备排障；</li>
 *   <li>随机 UUID —— 兜底。</li>
 * </ol>
 * <b>安全</b>：上游传入值只做长度与字符白名单校验，防止日志注入
 * （换行伪造日志行）与超长值撑爆日志字段。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String DEVICE_KEY_HEADER = "X-Device-Key";
    private static final int MAX_LENGTH = 64;

    /** 请求属性名，供下游（如摄取 worker）读取同一个 traceId */
    private static final String TRACE_ID_ATTRIBUTE = "com.mqtt.cloud.traceId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String traceId = sanitize(request.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = sanitize(request.getHeader(DEVICE_KEY_HEADER));
        }
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        // 回写响应头，前端 / 第三方排障时可直接拿到本次调用的 traceId
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须 finally 清理：线程池复用线程，残留会把下一次请求的日志打上错误的 traceId
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * 清洗上游传入的 traceId：仅接受字母数字与 {@code - _ .}，且限长。
     * 不符合则返回 {@code null} 由调用方重新生成。
     */
    private String sanitize(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
            if (!ok) {
                return null;
            }
        }
        return value;
    }

    /** 尽早执行，保证后续过滤器（含安全链）都在 traceId 上下文中。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
