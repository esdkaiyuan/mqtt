package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置（T-30 · E4）。
 * <p>
 * 仅作用于开放接口 {@code /external/v1/**}，按 API Key 维度做固定窗口计数。
 * <p>
 * <b>为什么默认关闭</b>：现网/演示环境若未显式配置就强制限流，会把正常调用直接打成 429。
 * 与其他能力的开关语义一致——{@code enabled=false} 表示「不限流」，
 * 而非「限流数为 0」。
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Data
public class RateLimitProperties {

    /** 限流总开关；false 时过滤器直接放行 */
    private boolean enabled = false;

    /** 固定窗口长度（秒） */
    private int windowSeconds = 60;

    /** 单个 API Key 在一个窗口内的最大调用次数 */
    private int maxRequestsPerWindow = 600;

    /** Redis 键前缀，便于与其他键空间区分 */
    private String keyPrefix = "mqtt:ratelimit:apikey:";

    /** Redis 不可用时是否放行。true=可用性优先（限流降级），false=严格限流 */
    private boolean failOpen = true;
}
