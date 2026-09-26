package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.WebhookConfigRequest;
import com.mqtt.cloud.entity.WebhookConfig;
import com.mqtt.cloud.service.WebhookConfigService;
import com.mqtt.cloud.service.WebhookDispatcher;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookConfigService webhookConfigService;
    private final WebhookDispatcher webhookDispatcher;

    public WebhookController(WebhookConfigService webhookConfigService,
                             WebhookDispatcher webhookDispatcher) {
        this.webhookConfigService = webhookConfigService;
        this.webhookDispatcher = webhookDispatcher;
    }

    @GetMapping
    public Result<List<WebhookConfig>> listWebhooks() {
        return Result.success(webhookConfigService.getUserWebhooks(SecurityUtils.requireUserId()));
    }

    @PostMapping
    public Result<WebhookConfig> createWebhook(@Valid @RequestBody WebhookConfigRequest request) {
        return Result.success(webhookConfigService.createWebhook(SecurityUtils.requireUserId(), request));
    }

    @GetMapping("/{id}")
    public Result<WebhookConfig> getWebhook(@PathVariable Long id) {
        return Result.success(requireOwnedWebhook(id));
    }

    @PutMapping("/{id}")
    public Result<WebhookConfig> updateWebhook(@PathVariable Long id,
                                               @Valid @RequestBody WebhookConfigRequest request) {
        WebhookConfig config = requireOwnedWebhook(id);
        config.setName(request.getName());
        config.setUrl(request.getUrl());
        config.setSecret(request.getSecret());
        config.setEvents(request.getEvents());
        config.setHeaders(request.getHeaders());
        config.setRetryCount(request.getRetryCount() != null ? request.getRetryCount() : 3);
        config.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 10);
        config.setIsActive(1);
        webhookConfigService.updateById(config);
        return Result.success(config);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteWebhook(@PathVariable Long id) {
        requireOwnedWebhook(id);
        webhookConfigService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    public Result<String> testWebhook(@PathVariable Long id) {
        return Result.success(webhookDispatcher.sendTest(requireOwnedWebhook(id)));
    }

    /**
     * 校验 Webhook 配置存在且属于当前用户，避免越权读写他人配置。
     */
    private WebhookConfig requireOwnedWebhook(Long id) {
        WebhookConfig config = webhookConfigService.getById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Webhook不存在");
        }
        if (!config.getUserId().equals(SecurityUtils.requireUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return config;
    }
}