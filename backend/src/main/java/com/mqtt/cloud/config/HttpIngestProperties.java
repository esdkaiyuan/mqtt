package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HTTP 上报接入配置（T-24 设计文档 §12）。
 * <p>
 * 前缀 {@code app.http-ingest}，字段全部带默认值，风格对齐 {@link SceneProperties} /
 * {@link com.mqtt.cloud.ingest.IngestProperties}。
 * <p>
 * {@code enabled} 为总开关：置 {@code false} 时 {@code HttpIngestController} 因
 * {@code @ConditionalOnProperty} 不注册，{@link com.mqtt.cloud.filter.DeviceCredentialAuthFilter}
 * 仍为 Bean 但 {@code shouldNotFilter} 直接放行，{@code /ingest/**} 因无处理器可命返回 {@code 404}；
 * MQTT 链路不受影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.http-ingest")
public class HttpIngestProperties {

    /** 是否启用 HTTP 上报端点与设备凭据过滤器；false 时 /ingest/** 返回 404，MQTT 链路不受影响 */
    private boolean enabled = true;

    /** 单次上报请求体大小上限（字节），超限返回 413 + 6248，防止大包打爆内存 */
    private int maxPayloadBytes = 65536;

    /** Basic 认证失败时 WWW-Authenticate 头的 realm 值 */
    private String realm = "mqtt-cloud-device";

    // ---- T-53 · HTTP 上报限流与幂等 ----

    /** 设备级限流开关；默认关闭，避免未配置环境被 429 打断 */
    private boolean rateLimitEnabled = false;

    /** 单设备 60 秒窗口内最大上报次数 */
    private int rateLimitPerMinute = 600;

    /** Redis 故障时是否放行（true=可用性优先） */
    private boolean rateLimitFailOpen = true;

    /** 限流 Redis 键前缀 */
    private String rateLimitKeyPrefix = "mqtt:ratelimit:ingest:";

    /** 幂等键 TTL（秒），默认 5 分钟，覆盖设备弱网重试窗口 */
    private int idempotencyTtlSeconds = 300;

    /** 幂等 Redis 键前缀 */
    private String idempotencyKeyPrefix = "mqtt:ingest:idem:";
}
