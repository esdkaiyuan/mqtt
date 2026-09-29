package com.mqtt.cloud.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.WebhookConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Webhook 事件分发器
 * <p>
 * 按事件类型匹配用户配置的 Webhook 并回调，签名与自定义请求头在此统一处理。
 * 设备事件回调走异步线程池，避免阻塞 MQTT 消息处理线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {

    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String HEADER_EVENT_TYPE = "X-Event-Type";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String EVENT_TEST = "webhook.test";

    private final WebhookConfigService webhookConfigService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Qualifier("webhookExecutor")
    private final ThreadPoolTaskExecutor webhookExecutor;

    /**
     * 将设备事件异步分发给所有订阅了该事件的 Webhook。
     */
    public void dispatch(Long deviceId, String eventType, Device device, String payload) {
        dispatch(webhookConfigService.getActiveWebhooksForDevice(device.getOwnerId(), deviceId), eventType, device, payload);
    }

    /**
     * 用调用方预先批量取好的 Webhook 列表分发，避免逐事件查询配置。
     */
    public void dispatch(List<WebhookConfig> webhooks, String eventType, Device device, String payload) {
        if (webhooks.isEmpty()) {
            return;
        }
        Map<String, Object> body = buildBody(eventType, device, payload);
        for (WebhookConfig webhook : webhooks) {
            if (!supportsEvent(webhook, eventType)) {
                continue;
            }
            webhookExecutor.execute(() -> deliver(webhook, eventType, body));
        }
    }

    /**
     * 同步发送一条测试消息，便于用户在控制台直接验证连通性。
     *
     * @throws BusinessException 当目标地址不可达或返回非 2xx 时抛出
     */
    public String sendTest(WebhookConfig webhook) {
        Map<String, Object> body = new HashMap<>();
        body.put("event", EVENT_TEST);
        body.put("message", "Webhook 连通性测试消息");
        body.put("timestamp", System.currentTimeMillis());

        ResponseEntity<String> response;
        try {
            response = post(webhook, EVENT_TEST, body);
        } catch (Exception e) {
            log.warn("Webhook 测试失败: name={}, url={}", webhook.getName(), webhook.getUrl(), e);
            throw new BusinessException(ResultCode.WEBHOOK_TEST_FAILED, "Webhook 测试失败: " + e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException(ResultCode.WEBHOOK_TEST_FAILED,
                    "Webhook 返回状态码 " + response.getStatusCode().value());
        }

        webhookConfigService.updateLastTriggered(webhook.getId());
        return "Webhook 测试成功，目标返回状态码 " + response.getStatusCode().value();
    }

    private boolean supportsEvent(WebhookConfig webhook, String eventType) {
        String events = webhook.getEvents();
        return events != null && events.contains(eventType);
    }

    private void deliver(WebhookConfig webhook, String eventType, Map<String, Object> body) {
        try {
            ResponseEntity<String> response = post(webhook, eventType, body);
            if (response.getStatusCode().is2xxSuccessful()) {
                webhookConfigService.updateLastTriggered(webhook.getId());
                log.info("Webhook 触发成功: name={}, url={}, event={}", webhook.getName(), webhook.getUrl(), eventType);
            } else {
                log.warn("Webhook 返回非 2xx: name={}, status={}", webhook.getName(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Webhook 触发失败: name={}, url={}", webhook.getName(), webhook.getUrl(), e);
        }
    }

    private ResponseEntity<String> post(WebhookConfig webhook, String eventType, Map<String, Object> body) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(body);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_EVENT_TYPE, eventType);

        if (webhook.getSecret() != null && !webhook.getSecret().isBlank()) {
            headers.set(HEADER_SIGNATURE, "sha256=" + sign(webhook.getSecret(), jsonPayload));
        }
        applyCustomHeaders(webhook, headers);

        return restTemplate.postForEntity(webhook.getUrl(), new HttpEntity<>(jsonPayload, headers), String.class);
    }

    private Map<String, Object> buildBody(String eventType, Device device, String payload) {
        Map<String, Object> body = new HashMap<>();
        body.put("event", eventType);
        body.put("deviceKey", device.getDeviceKey());
        body.put("deviceName", device.getDeviceName());
        body.put("deviceType", device.getDeviceType());
        body.put("topic", device.getTopic());
        body.put("status", device.getStatus());
        body.put("payload", payload);
        body.put("timestamp", System.currentTimeMillis());
        return body;
    }

    private void applyCustomHeaders(WebhookConfig webhook, HttpHeaders headers) throws Exception {
        if (webhook.getHeaders() == null || webhook.getHeaders().isBlank()) {
            return;
        }
        Map<String, String> customHeaders = objectMapper.readValue(webhook.getHeaders(), new TypeReference<>() {
        });
        customHeaders.forEach(headers::set);
    }

    private String sign(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}