package com.mqtt.cloud.service.notification;

import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.WebhookConfig;
import com.mqtt.cloud.service.WebhookConfigService;
import com.mqtt.cloud.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * 既有 Webhook 出口的适配器（内置通道 {@code WEBHOOK}，T-27 设计文档 §4.1）。
 * <p>
 * 把 T-17 既有的告警 Webhook 出口注册为内置通道，使其投递在 {@code notification_delivery}
 * 中可见。**不新建投递路径**：转发 {@link WebhookDispatcher#dispatch(List, String, Device, String)}
 * 的既有分发，签名、自定义请求头、失败计数与异步语义保持原样。
 * <p>
 * 既有分发为 fire-and-forget，本适配器无法观测异步投递的最终结果，因此以「是否命中订阅该事件的
 * 活动 Webhook」作为结果：无命中返回 {@link ChannelSendResult#skipped()} 不写投递记录，
 * 命中则记一条 {@code SUCCESS} 表示已移交既有分发链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookChannelAdapter implements NotificationChannel {

    private final WebhookDispatcher webhookDispatcher;
    private final WebhookConfigService webhookConfigService;

    @Override
    public String channelType() {
        return NotificationConstants.CHANNEL_WEBHOOK;
    }

    @Override
    public ChannelSendResult send(NotificationMessage message) {
        Device device = message.getDevice();
        if (device == null) {
            return ChannelSendResult.skipped();
        }
        List<WebhookConfig> matched = matchingWebhooks(device, message.getEventType());
        if (matched.isEmpty()) {
            return ChannelSendResult.skipped();
        }
        webhookDispatcher.dispatch(matched, message.getEventType(), device, message.getPayloadJson());
        return ChannelSendResult.success(host(matched.get(0).getUrl()));
    }

    /** 取该设备命中订阅此事件的活动 Webhook；与既有分发的事件匹配口径一致（{@code events} 包含事件名）。 */
    private List<WebhookConfig> matchingWebhooks(Device device, String eventType) {
        if (device.getOwnerId() == null || device.getId() == null) {
            return List.of();
        }
        return webhookConfigService.getActiveWebhooksForDevice(device.getOwnerId(), device.getId()).stream()
                .filter(webhook -> webhook.getEvents() != null && webhook.getEvents().contains(eventType))
                .toList();
    }

    private static String host(String url) {
        if (url == null) {
            return null;
        }
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}