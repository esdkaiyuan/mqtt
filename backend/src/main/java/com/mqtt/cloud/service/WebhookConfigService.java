package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.WebhookConfigRequest;
import com.mqtt.cloud.entity.WebhookConfig;

import java.util.List;

public interface WebhookConfigService extends IService<WebhookConfig> {

    WebhookConfig createWebhook(Long userId, WebhookConfigRequest request);

    List<WebhookConfig> getUserWebhooks(Long userId);

    List<WebhookConfig> getActiveWebhooksForDevice(Long userId, Long deviceId);

    void updateLastTriggered(Long webhookId);
}
