package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.ApiKeyRequest;
import com.mqtt.cloud.entity.ApiKey;
import com.mqtt.cloud.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public Result<List<ApiKey>> listApiKeys() {
        return Result.success(apiKeyService.getUserApiKeys(SecurityUtils.requireUserId()));
    }

    @PostMapping
    public Result<ApiKey> createApiKey(@Valid @RequestBody ApiKeyRequest request) {
        return Result.success(apiKeyService.createApiKey(SecurityUtils.requireUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> revokeApiKey(@PathVariable Long id) {
        requireOwnedApiKey(id);
        apiKeyService.removeById(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ApiKey> getApiKey(@PathVariable Long id) {
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