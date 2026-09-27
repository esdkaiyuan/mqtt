package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.WebhookConfigRequest;
import com.mqtt.cloud.entity.WebhookConfig;
import com.mqtt.cloud.service.WebhookConfigService;
import com.mqtt.cloud.service.WebhookDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Webhook", description = "设备事件 Webhook 配置管理，仅可操作本人配置")
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

    @Operation(summary = "获取Webhook列表", description = "返回当前用户的全部 Webhook 配置")
    @GetMapping
    public Result<List<WebhookConfig>> listWebhooks() {
        return Result.success(webhookConfigService.getUserWebhooks(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "创建Webhook", description = "创建 Webhook 配置，可指定事件类型、密钥、请求头、重试次数与超时时间")
    @PostMapping
    public Result<WebhookConfig> createWebhook(@Valid @RequestBody WebhookConfigRequest request) {
        return Result.success(webhookConfigService.createWebhook(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "获取Webhook详情", description = "按 ID 查询 Webhook 配置，仅所有者可访问，越权返回 403")
    @GetMapping("/{id}")
    public Result<WebhookConfig> getWebhook(
            @Parameter(description = "Webhook ID", required = true) @PathVariable Long id) {
        return Result.success(requireOwnedWebhook(id));
    }

    @Operation(summary = "更新Webhook", description = "更新 Webhook 的名称、URL、事件、请求头、重试次数与超时时间")
    @PutMapping("/{id}")
    public Result<WebhookConfig> updateWebhook(
            @Parameter(description = "Webhook ID", required = true) @PathVariable Long id,
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

    @Operation(summary = "删除Webhook", description = "删除指定 Webhook 配置，仅所有者可操作，越权返回 403")
    @DeleteMapping("/{id}")
    public Result<Void> deleteWebhook(
            @Parameter(description = "Webhook ID", required = true) @PathVariable Long id) {
        requireOwnedWebhook(id);
        webhookConfigService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "测试Webhook", description = "向配置的 URL 提交一次测试投递任务，返回提交结果")
    @PostMapping("/{id}/test")
    public Result<String> testWebhook(
            @Parameter(description = "Webhook ID", required = true) @PathVariable Long id) {
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