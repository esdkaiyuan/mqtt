package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.ApiKeyRequest;
import com.mqtt.cloud.entity.ApiKey;
import com.mqtt.cloud.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "API密钥", description = "外部开放接口所用的 API Key 管理，仅可操作本人密钥")
@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Operation(summary = "获取密钥列表", description = "返回当前用户的全部 API Key（不含密钥明文）")
    @GetMapping
    public Result<List<ApiKey>> listApiKeys() {
        return Result.success(apiKeyService.getUserApiKeys(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "创建密钥", description = "创建 API Key，完整密钥明文仅在创建响应中返回一次，请立即保存")
    @PostMapping
    public Result<ApiKey> createApiKey(@Valid @RequestBody ApiKeyRequest request) {
        return Result.success(apiKeyService.createApiKey(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "吊销密钥", description = "删除指定 API Key，仅所有者可操作，越权返回 403")
    @DeleteMapping("/{id}")
    public Result<Void> revokeApiKey(
            @Parameter(description = "密钥ID", required = true) @PathVariable Long id) {
        requireOwnedApiKey(id);
        apiKeyService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "获取密钥详情", description = "按 ID 查询 API Key，仅所有者可访问，越权返回 403")
    @GetMapping("/{id}")
    public Result<ApiKey> getApiKey(
            @Parameter(description = "密钥ID", required = true) @PathVariable Long id) {
        return Result.success(requireOwnedApiKey(id));
    }

    /**
     * 校验 API Key 存在且属于当前用户，避免越权读取/删除他人密钥。
     */
    private ApiKey requireOwnedApiKey(Long id) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!apiKey.getUserId().equals(SecurityUtils.requireUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return apiKey;
    }
}