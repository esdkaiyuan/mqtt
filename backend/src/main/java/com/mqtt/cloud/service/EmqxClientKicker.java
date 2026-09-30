package com.mqtt.cloud.service;

import tools.jackson.databind.ObjectMapper;
import com.mqtt.cloud.config.EmqxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * EMQX 踢线客户端：按设备用户名断开其在线连接，实现「禁用即切断」。
 * <p>
 * 禁用 / 停用只改变认证与授权判定，EMQX 不会主动断开已建立的会话，连接态最迟在授权
 * 缓存 TTL 内收敛。本组件在状态变更后调用 {@code DELETE /api/v5/clients/{clientid}}，
 * 把连接态收敛从「秒级」压缩到「立即」。
 * <p>
 * 踢线是尽力而为的加速手段，禁用本身已由准入判定保证，因此全部失败路径只记 WARN 并返回 0，
 * 绝不向调用方抛异常——踢线失败不能连累禁用这一业务动作。
 */
@Slf4j
@Component
public class EmqxClientKicker {

    /** EMQX token 无 exp 声明时的兜底有效期 */
    private static final long DEFAULT_TOKEN_TTL_MS = 30 * 60 * 1000L;
    /** 提前量：避免踩在过期边界上发起请求 */
    private static final long TOKEN_SAFETY_MARGIN_MS = 60 * 1000L;

    private final RestTemplate restTemplate;
    private final EmqxProperties properties;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile long cachedTokenExpiresAt;

    public EmqxClientKicker(RestTemplate restTemplate, EmqxProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 断开指定用户名下的全部在线连接，返回实际踢除的连接数。
     * <p>
     * 用户名格式为 {@code {productKey}.{deviceKey}}，与设备认证用户名一致。
     */
    public int kickByUsername(String username) {
        if (!properties.isKickEnabled() || username == null || username.isBlank()) {
            return 0;
        }
        try {
            String token = obtainToken();
            List<String> clientIds = listClientIds(token, username);
            int kicked = 0;
            for (String clientId : clientIds) {
                if (kick(token, clientId)) {
                    kicked++;
                }
            }
            if (kicked > 0) {
                log.info("已踢下线设备连接: username={}, count={}", username, kicked);
            }
            return kicked;
        } catch (Exception e) {
            log.warn("踢下线失败，降级为等待授权缓存 TTL 收敛连接态: username={}", username, e);
            return 0;
        }
    }

    private String obtainToken() throws Exception {
        String token = cachedToken;
        if (token != null && System.currentTimeMillis() < cachedTokenExpiresAt) {
            return token;
        }
        synchronized (this) {
            if (cachedToken != null && System.currentTimeMillis() < cachedTokenExpiresAt) {
                return cachedToken;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = objectMapper.writeValueAsString(Map.of(
                    "username", properties.getDashboardUser(),
                    "password", properties.getDashboardPassword()));
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getApiBaseUrl() + "/login", HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            String fresh = asString(parse(response.getBody()).get("token"));
            if (fresh == null || fresh.isBlank()) {
                throw new IllegalStateException("EMQX 登录响应缺少 token");
            }
            cachedToken = fresh;
            cachedTokenExpiresAt = resolveExpiryMillis(fresh) - TOKEN_SAFETY_MARGIN_MS;
            return fresh;
        }
    }

    private List<String> listClientIds(String token, String username) throws Exception {
        String url = properties.getApiBaseUrl() + "/clients?limit=100&username="
                + UriUtils.encodeQueryParam(username, StandardCharsets.UTF_8);
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        Object data = parse(response.getBody()).get("data");
        if (!(data instanceof List<?> clients)) {
            return List.of();
        }
        List<String> clientIds = new ArrayList<>();
        for (Object client : clients) {
            if (client instanceof Map<?, ?> fields && fields.get("clientid") instanceof String clientId
                    && !clientId.isBlank()) {
                clientIds.add(clientId);
            }
        }
        return clientIds;
    }

    private boolean kick(String token, String clientId) {
        try {
            String url = properties.getApiBaseUrl() + "/clients/"
                    + UriUtils.encodePathSegment(clientId, StandardCharsets.UTF_8);
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), String.class);
            return true;
        } catch (Exception e) {
            log.warn("踢下线单个客户端失败: clientId={}", clientId, e);
            return false;
        }
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(body, Map.class);
    }

    private String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /** EMQX Dashboard token 的 exp 声明为毫秒时间戳；解析失败时按默认 TTL 兜底。 */
    private long resolveExpiryMillis(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                if (parse(payload).get("exp") instanceof Number exp) {
                    return exp.longValue();
                }
            }
        } catch (Exception e) {
            log.debug("解析 EMQX token 过期时间失败，按默认 TTL 兜底", e);
        }
        return System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS;
    }
}