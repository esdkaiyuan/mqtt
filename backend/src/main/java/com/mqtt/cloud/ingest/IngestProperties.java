package com.mqtt.cloud.ingest;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ingest")
public class IngestProperties {

    /** false 时回退为回调线程同步落库（仅用于故障回滚） */
    private boolean enabled = true;

    /** 单批最大条数 */
    private int batchSize = 500;

    /** 攒批最长等待（毫秒），与 batch-size 先到者触发 flush */
    private long flushIntervalMs = 200L;

    /** 单 worker 队列容量，总容量 = queue-capacity * worker-count */
    private int queueCapacity = 20000;

    /** worker 数，同时决定 deviceKey 的路由分片数 */
    private int workerCount = 4;

    /** 入队等待上限（毫秒），超时视为溢出 */
    private long offerTimeoutMs = 50L;

    /** 落库失败重试次数与退避基数（毫秒） */
    private int maxAttempts = 3;

    private long retryBackoffMs = 500L;

    /** 停机时排空队列的最长等待（毫秒） */
    private long shutdownDrainTimeoutMs = 10000L;
}