package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mqtt.cloud.dto.request.ApiKeyRequest;
import com.mqtt.cloud.entity.ApiKey;

import java.util.List;

public interface ApiKeyService extends IService<ApiKey> {

    ApiKey createApiKey(Long userId, ApiKeyRequest request);

    List<ApiKey> getUserApiKeys(Long userId);

    ApiKey validateApiKey(String keyValue);

    void updateLastUsed(Long apiKeyId);
}
