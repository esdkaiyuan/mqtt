package com.mqtt.cloud.filter;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.config.HttpIngestProperties;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 设备凭据鉴权过滤器（T-24 多协议接入）。
 * <p>
 * 仅作用于 {@code /ingest/**} HTTP 上报端点，使用 HTTP Basic 校验设备凭据
 * （用户名 {@code productKey.deviceKey}，密码为设备密钥），校验通过后将
 * {@code deviceKey} 写入请求属性 {@link #AUTHENTICATED_DEVICE_KEY}，
 * 由后续的 HTTP 上报服务取出，构造与 MQTT 同形的 {@code IngestRecord} 投递进摄取管线。
 * <p>
 * 失败路径（无头 / 非 Basic / base64 非法 / 用户名格式非法 / 路径与凭据不一致 /
 * 设备不存在或被禁 / 密码错误）<b>统一</b>返回 {@code 401} + 错误码 {@code 6246}，
 * 并附带 {@code WWW-Authenticate} 头；不区分失败原因，避免向调用方泄露设备是否存在。
 */
@Component
@RequiredArgsConstructor
public class DeviceCredentialAuthFilter extends OncePerRequestFilter {

    /** 校验通过后写入请求属性的设备 Key，供 HTTP 上报服务读取。 */
    public static final String AUTHENTICATED_DEVICE_KEY = "com.mqtt.cloud.filter.AUTHENTICATED_DEVICE_KEY";

    private static final String INGEST_PREFIX = "/ingest/";
    private static final String BASIC_PREFIX = "Basic ";

    private final DeviceSecretService deviceSecretService;
    private final DeviceAccessGuard deviceAccessGuard;
    private final HttpIngestProperties httpIngestProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !httpIngestProperties.isEnabled() || !resolvePath(request).startsWith(INGEST_PREFIX);
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

        String[] credential = parseCredential(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (credential == null) {
            reject(response);
            return;
        }

        String[] username = deviceSecretService.parseUsername(credential[0]);
        if (username == null) {
            reject(response);
            return;
        }
        String productKey = username[0];
        String deviceKey = username[1];

        if (!deviceKey.equals(extractDeviceKey(resolvePath(request)))) {
            reject(response);
            return;
        }

        DeviceAuthCacheService.AuthMeta meta = deviceAccessGuard.resolve(productKey, deviceKey);
        if (!deviceAccessGuard.isPermitted(meta)) {
            reject(response);
            return;
        }

        if (!deviceSecretService.matches(credential[1], meta.secretHash())) {
            reject(response);
            return;
        }

        request.setAttribute(AUTHENTICATED_DEVICE_KEY, deviceKey);
        filterChain.doFilter(request, response);
    }

    /**
     * 解析 Basic 头为 {@code [username, secret]}；非 Basic、base64 非法或缺少分隔冒号时返回 {@code null}。
     */
    private String[] parseCredential(String header) {
        if (header == null || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim()),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int idx = decoded.indexOf(':');
        if (idx < 0) {
            return null;
        }
        return new String[]{decoded.substring(0, idx), decoded.substring(idx + 1)};
    }

    /** 从 {@code /ingest/{deviceKey}/...} 中取出路径段的 deviceKey。 */
    private String extractDeviceKey(String path) {
        String rest = path.substring(INGEST_PREFIX.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    /** 统一的失败响应：401 + WWW-Authenticate + 错误码 6246，且不继续过滤链。 */
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + httpIngestProperties.getRealm() + "\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Result.error(ResultCode.DEVICE_CREDENTIAL_INVALID));
    }
}
