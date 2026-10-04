package com.mqtt.cloud.service;

import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.config.RuleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * 规则 HTTP 转发器（T-19 设计文档 §5.3 / §8.5）。
 * <p>
 * 独立于 {@link WebhookDispatcher}：目标地址 / 载荷模板 / 超时都按规则配置，但**签名方案保持一致**
 * （{@code X-Signature: sha256=<hex>}，HMAC-SHA256 覆盖原始 body 字节），用户侧验签逻辑只需实现一次。
 * <p>
 * 请求头：{@code Content-Type} / {@code X-Event-Type}（{@code rule.triggered}）/ {@code X-Rule-Id} /
 * {@code X-Execution-Id}（幂等键，重试不变）/ {@code X-Signature}（密钥为空时不下发）。
 * 不跟随重定向；非 2xx 视为失败并抛出，交由动作执行的重试兜底。
 */
@Slf4j
@Component
public class RuleHttpForwarder {

    private static final String HEADER_EVENT_TYPE = "X-Event-Type";
    private static final String HEADER_RULE_ID = "X-Rule-Id";
    private static final String HEADER_EXECUTION_ID = "X-Execution-Id";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RuleProperties properties;
    private final RestTemplate restTemplate;

    public RuleHttpForwarder(RuleProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getHttpTimeoutMs());
        factory.setReadTimeout(properties.getHttpTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 发送规则转发请求（T-19 既有入口，行为与签名不变）。
     *
     * @param method      HTTP 方法（POST / PUT）
     * @param url         目标地址
     * @param customHeaders 自定义请求头（已渲染）
     * @param body        请求体（已渲染的 JSON 文本）
     * @param ruleId      规则 ID（请求头）
     * @param executionId 执行记录 ID（幂等键，重试不变）
     * @throws IllegalStateException 目标不可达或返回非 2xx
     */
    public void send(String method, String url, Map<String, String> customHeaders,
                     String body, Long ruleId, Long executionId) {
        send(RuleConstants.EVENT_RULE_TRIGGERED, HEADER_RULE_ID, ruleId, method, url,
                customHeaders, body, executionId);
    }

    /**
     * 发送转发请求（T-23 通用入口）：把事件类型 / 来源头名 / 来源 ID 参数化，供规则与场景共用同一套出站与签名。
     * <p>
     * 规则传 {@code eventType=rule.triggered}、{@code sourceIdHeader=X-Rule-Id}；
     * 场景传 {@code eventType=scene.triggered}、{@code sourceIdHeader=X-Scene-Id}。
     * 签名方案与请求头集合完全一致，用户侧验签逻辑只需实现一次。
     *
     * @param eventType      事件类型（{@code X-Event-Type}）
     * @param sourceIdHeader 来源 ID 请求头名（如 {@code X-Rule-Id} / {@code X-Scene-Id}）
     * @param sourceId       来源 ID 取值（请求头）
     * @param method         HTTP 方法（POST / PUT）
     * @param url            目标地址
     * @param customHeaders  自定义请求头（已渲染）
     * @param body           请求体（已渲染的 JSON 文本）
     * @param executionId    执行记录 ID（幂等键，重试不变）
     * @throws IllegalStateException 目标不可达或返回非 2xx
     */
    public void send(String eventType, String sourceIdHeader, Long sourceId, String method, String url,
                     Map<String, String> customHeaders, String body, Long executionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        if (eventType != null && !eventType.isBlank()) {
            headers.set(HEADER_EVENT_TYPE, eventType);
        }
        if (sourceIdHeader != null && !sourceIdHeader.isBlank() && sourceId != null) {
            headers.set(sourceIdHeader, String.valueOf(sourceId));
        }
        if (executionId != null) {
            headers.set(HEADER_EXECUTION_ID, String.valueOf(executionId));
        }
        String secret = properties.getHttpSecret();
        if (secret != null && !secret.isBlank()) {
            headers.set(HEADER_SIGNATURE, "sha256=" + sign(secret, body));
        }
        if (customHeaders != null) {
            customHeaders.forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.set(key, value);
                }
            });
        }

        String httpMethod = method == null || method.isBlank() ? HttpMethod.POST.name() : method.toUpperCase();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(httpMethod), new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("转发失败：目标返回状态码 " + response.getStatusCode().value());
            }
            log.info("HTTP 转发成功: eventType={}, sourceId={}, executionId={}, url={}",
                    eventType, sourceId, executionId, url);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("转发失败：" + e.getMessage(), e);
        }
    }

    private String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名计算失败：" + e.getMessage(), e);
        }
    }
}