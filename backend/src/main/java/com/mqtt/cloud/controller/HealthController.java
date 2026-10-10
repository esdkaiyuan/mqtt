package com.mqtt.cloud.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查端点（T-30 · E3）。
 * <p>
 * 改造前只返回 {@code status/database/redis/timestamp}，无法作为存活探针使用——
 * 数据库与 Redis 任一不可用都会把 status 打成 DOWN 导致容器被重启；
 * 而真正需要立刻介入的「摄取队列积压」「死信积压」恰恰不在返回里。
 * <p>
 * 现拆分为三个语义：
 * <ul>
 *   <li>{@code /health} —— 存活探针（liveness）：进程能响应即 UP，不受下游拖累；</li>
 *   <li>{@code /health/ready} —— 就绪探针（readiness）：依赖不可用或积压超阈值时 DOWN；</li>
 *   <li>{@code /health/detail} —— 排障明细：队列深度（按 worker）、死信三态等。</li>
 * </ul>
 * Docker healthcheck 挂 liveness（避免数据库抖动引发重启风暴），负载均衡与监控用 readiness。
 */
@Tag(name = "健康检查", description = "存活/就绪探针与排障明细")
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final com.mqtt.cloud.service.IngestDeadLetterService deadLetterService;

    /**
     * 死信积压阈值：PENDING 死信超过该条数即判 readiness DOWN。
     * 死信意味着「上行消息在丢失」，属必须尽快人工介入的状态，不适合等待自动恢复。
     */
    @Value("${app.health.dead-letter-threshold:1000}")
    private long deadLetterThreshold = 1000L;

    /** 摄取队列总深度阈值：积压超过该值说明消费能力跟不上写入。 */
    @Value("${app.health.queue-depth-threshold:5000}")
    private long queueDepthThreshold = 5000L;

    @Operation(summary = "存活探针",
            description = "进程级存活检查，能响应即返回 UP，不受数据库/Redis 影响；"
                    + "供 Docker healthcheck 使用，避免下游抖动引发重启风暴")
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    @Operation(summary = "就绪探针",
            description = "依赖与积压联合判定：数据库/Redis 任一不可用，或摄取队列深度、"
                    + "死信积压超阈值时返回 DOWN 并附 degraded 原因。供负载均衡与外部监控使用")
    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        Map<String, String> degraded = new HashMap<>();

        if (!checkDatabase()) {
            degraded.put("database", "DOWN");
        }
        if (!checkRedis()) {
            degraded.put("redis", "DOWN");
        }

        long queueDepth = totalQueueDepth();
        result.put("ingestQueueDepth", queueDepth);
        if (queueDepth > queueDepthThreshold) {
            degraded.put("ingestQueue", "BACKLOGGED");
        }

        // 死信计数查询失败不额外把探针打挂（DB 问题已由 database 项反映）
        try {
            long pending = deadLetterService.countByStatus()
                    .getOrDefault(com.mqtt.cloud.entity.IngestDeadLetter.STATUS_PENDING, 0L);
            result.put("deadLetterPending", pending);
            if (pending > deadLetterThreshold) {
                degraded.put("deadLetter", "BACKLOGGED");
            }
        } catch (Exception e) {
            degraded.put("deadLetter", "UNKNOWN");
        }

        result.put("status", degraded.isEmpty() ? "UP" : "DOWN");
        result.put("degraded", degraded);
        return result;
    }

    @Operation(summary = "排障明细",
            description = "返回依赖状态、摄取队列深度（按 worker 维度）与死信三态计数，供运维定位。仅 ADMIN")
    @GetMapping("/detail")
    public Map<String, Object> detail() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        result.put("database", checkDatabase() ? "UP" : "DOWN");
        result.put("redis", checkRedis() ? "UP" : "DOWN");
        result.put("ingestQueueDepth", totalQueueDepth());

        // 按 worker 展开：定位是单分片积压还是全局积压
        Map<String, Object> byWorker = new HashMap<>();
        meterRegistry.getMeters().stream()
                .filter(m -> "ingest.queue.depth".equals(m.getId().getName()))
                .forEach(m -> {
                    Gauge gauge = meterRegistry.find(m.getId().getName())
                            .tags(m.getId().getTags())
                            .gauge();
                    if (gauge != null) {
                        byWorker.put(m.getId().getTag("worker"), gauge.value());
                    }
                });
        result.put("ingestQueueDepthByWorker", byWorker);

        try {
            result.put("deadLetter", deadLetterService.countByStatus());
        } catch (Exception e) {
            result.put("deadLetter", "UNAVAILABLE");
        }
        return result;
    }

    private boolean checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRedis() {
        try {
            redisTemplate.opsForValue().get("health-check");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 汇总所有 worker 分片的队列深度。 */
    private long totalQueueDepth() {
        double sum = meterRegistry.getMeters().stream()
                .filter(m -> "ingest.queue.depth".equals(m.getId().getName()))
                .mapToDouble(m -> {
                    Gauge gauge = meterRegistry.find(m.getId().getName())
                            .tags(m.getId().getTags())
                            .gauge();
                    return gauge == null ? 0D : gauge.value();
                })
                .sum();
        return (long) sum;
    }
}
