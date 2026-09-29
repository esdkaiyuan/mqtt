package com.mqtt.cloud.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAuthCacheServiceImplTest {

    private static final String KEY = "auth:meta:esp32-fall:sensor-01";

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AccessControlProperties properties;
    private DeviceAuthCacheServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AccessControlProperties();
        properties.setCacheTtlSeconds(60);
        service = new DeviceAuthCacheServiceImpl(redisTemplate, objectMapper, properties);
    }

    @Test
    void get_should_return_null_without_touching_redis_when_cache_disabled() {
        properties.setCacheTtlSeconds(0);

        assertThat(service.get("esp32-fall", "sensor-01")).isNull();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void put_should_serialize_meta_with_ttl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        DeviceAuthCacheService.AuthMeta meta =
                new DeviceAuthCacheService.AuthMeta(1L, "ENABLED", 1, "$2a$hash");

        service.put("esp32-fall", "sensor-01", meta);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), json.capture(), eq(Duration.ofSeconds(60)));
        assertThat(objectMapper.readValue(json.getValue(), DeviceAuthCacheService.AuthMeta.class))
                .isEqualTo(meta);
    }

    @Test
    void get_should_deserialize_hit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(
                objectMapper.writeValueAsString(new DeviceAuthCacheService.AuthMeta(1L, "ENABLED", 1, "$2a$hash")));

        assertThat(service.get("esp32-fall", "sensor-01"))
                .isEqualTo(new DeviceAuthCacheService.AuthMeta(1L, "ENABLED", 1, "$2a$hash"));
    }

    @Test
    void get_should_degrade_to_miss_when_redis_unavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.get("esp32-fall", "sensor-01")).isNull();
    }

    @Test
    void evict_should_delete_single_key() {
        service.evict("esp32-fall", "sensor-01");

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void evictProduct_should_delete_all_keys_of_product() {
        Set<String> keys = Set.of("auth:meta:esp32-fall:d1", "auth:meta:esp32-fall:d2");
        when(redisTemplate.keys("auth:meta:esp32-fall:*")).thenReturn(keys);

        service.evictProduct("esp32-fall");

        verify(redisTemplate).delete(keys);
    }

    @Test
    void evictProduct_should_skip_delete_when_no_keys() {
        when(redisTemplate.keys("auth:meta:esp32-fall:*")).thenReturn(Set.of());

        service.evictProduct("esp32-fall");

        verify(redisTemplate, never()).delete(anyCollection());
    }
}