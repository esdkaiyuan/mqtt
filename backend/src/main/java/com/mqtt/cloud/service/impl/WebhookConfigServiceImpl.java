package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mqtt.cloud.dto.request.WebhookConfigRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.WebhookConfig;
import com.mqtt.cloud.mapper.WebhookConfigMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.WebhookConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WebhookConfigServiceImpl extends ServiceImpl<WebhookConfigMapper, WebhookConfig> implements WebhookConfigService {

    private final DeviceService deviceService;

    public WebhookConfigServiceImpl(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebhookConfig createWebhook(Long userId, WebhookConfigRequest request) {
        WebhookConfig config = new WebhookConfig();
        config.setUserId(userId);
        config.setName(request.getName());
        config.setUrl(request.getUrl());
        config.setSecret(request.getSecret());
        config.setEvents(request.getEvents());
        config.setHeaders(request.getHeaders());
        config.setRetryCount(request.getRetryCount() != null ? request.getRetryCount() : 3);
        config.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 10);
        config.setIsActive(1);

        if (request.getDeviceKey() != null && !request.getDeviceKey().isBlank()) {
            Device device = deviceService.getDeviceByKey(request.getDeviceKey());
            if (device != null) {
                config.setDeviceId(device.getId());
            }
        }

        save(config);
        return config;
    }

    @Override
    public List<WebhookConfig> getUserWebhooks(Long userId) {
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getUserId, userId)
               .orderByDesc(WebhookConfig::getCreatedAt);
        return list(wrapper);
    }

    @Override
    public List<WebhookConfig> getActiveWebhooksForDevice(Long userId, Long deviceId) {
        return baseMapper.findActiveByUserAndDevice(userId, deviceId);
    }

    @Override
    public void updateLastTriggered(Long webhookId) {
        WebhookConfig config = new WebhookConfig();
        config.setId(webhookId);
        config.setLastTriggeredAt(LocalDateTime.now());
        config.setFailureCount(0);
        updateById(config);
    }
}
