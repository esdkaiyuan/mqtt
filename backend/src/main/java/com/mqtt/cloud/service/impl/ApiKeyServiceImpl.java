package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mqtt.cloud.dto.request.ApiKeyRequest;
import com.mqtt.cloud.entity.ApiKey;
import com.mqtt.cloud.mapper.ApiKeyMapper;
import com.mqtt.cloud.service.ApiKeyService;
import com.mqtt.cloud.util.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {

    /** last_used_at 刷新节流窗口：同一密钥在该窗口内只写库一次，避免每个请求都产生一次 UPDATE */
    private static final String LAST_USED_THROTTLE_PREFIX = "apikey:last-used:";
    private static final long LAST_USED_THROTTLE_SECONDS = 60;

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKey createApiKey(Long userId, ApiKeyRequest request) {
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setName(request.getName());
        apiKey.setKeyValue(ApiKeyGenerator.generateKey());
        apiKey.setPermissions(request.getPermissions());
        apiKey.setIsActive(1);

        if (request.getExpiresAt() != null && !request.getExpiresAt().isBlank()) {
            apiKey.setExpiresAt(LocalDateTime.parse(request.getExpiresAt().replace("Z", "")));
        }

        save(apiKey);
        return apiKey;
    }

    @Override
    public List<ApiKey> getUserApiKeys(Long userId) {
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKey::getUserId, userId)
               .orderByDesc(ApiKey::getCreatedAt);
        return list(wrapper);
    }

    @Override
    public ApiKey validateApiKey(String keyValue) {
        if (keyValue == null || keyValue.isBlank()) {
            return null;
        }
        ApiKey apiKey = baseMapper.findByKeyValue(keyValue);
        if (apiKey == null || apiKey.getIsActive() == null || apiKey.getIsActive() != 1) {
            return null;
        }
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return apiKey;
    }

    @Override
    public void updateLastUsed(Long apiKeyId) {
        if (apiKeyId == null || !acquireLastUsedSlot(apiKeyId)) {
            return;
        }
        ApiKey apiKey = new ApiKey();
        apiKey.setId(apiKeyId);
        apiKey.setLastUsedAt(LocalDateTime.now());
        updateById(apiKey);
    }

    /**
     * 通过 Redis 抢占节流槽位。Redis 不可用时降级为直接写库，保证功能不受影响。
     */
    private boolean acquireLastUsedSlot(Long apiKeyId) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    LAST_USED_THROTTLE_PREFIX + apiKeyId,
                    "1",
                    LAST_USED_THROTTLE_SECONDS,
                    TimeUnit.SECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.debug("API Key last_used 节流失败，按未节流处理: {}", e.getMessage());
            return true;
        }
    }
}
