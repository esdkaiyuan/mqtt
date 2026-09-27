package com.mqtt.cloud.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查端点
 */
@Tag(name = "健康检查", description = "服务与依赖（数据库、Redis）健康状态探测，公开访问")
@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Operation(summary = "健康检查", description = "返回 status/database/redis/timestamp；任一依赖异常时 status 为 DOWN。该接口不包装 Result，直接返回 Map")
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            result.put("database", "UP");
        } catch (Exception e) {
            result.put("database", "DOWN");
            result.put("status", "DOWN");
        }

        try {
            redisTemplate.opsForValue().get("health-check");
            result.put("redis", "UP");
        } catch (Exception e) {
            result.put("redis", "DOWN");
            result.put("status", "DOWN");
        }

        return result;
    }
}