package com.mqtt.cloud.ingest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

/**
 * 摄取链路的集中指标注册。
 * <p>
 * 指标名在 Prometheus 中会转换为下划线形式（如 {@code ingest.dropped} → {@code ingest_dropped_total}）。
 */
@Component
public class IngestMetrics {

    private static final String REASON = "reason";

    private final MeterRegistry registry;

    private final Counter submitted;
    private final Counter persisted;
    private final Counter batchFailures;
    private final Timer persistDuration;
    private final DistributionSummary batchSize;

    public IngestMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.submitted = Counter.builder("ingest.submitted")
                .description("提交到摄取队列的消息数")
                .register(registry);
        this.persisted = Counter.builder("ingest.persisted")
                .description("成功落库的消息数")
                .register(registry);
        this.batchFailures = Counter.builder("ingest.batch.failures")
                .description("重试耗尽后失败的批次数")
                .register(registry);
        this.persistDuration = Timer.builder("ingest.persist.duration")
                .description("单批落库耗时")
                .register(registry);
        this.batchSize = DistributionSummary.builder("ingest.batch.size")
                .description("单批落库条数")
                .register(registry);
    }

    public Counter submitted() {
        return submitted;
    }

    public Counter persisted() {
        return persisted;
    }

    public Counter batchFailures() {
        return batchFailures;
    }

    public Timer persistDuration() {
        return persistDuration;
    }

    public DistributionSummary batchSize() {
        return batchSize;
    }

    /** 丢弃计数：reason = queue_full / unknown_device */
    public Counter dropped(String reason) {
        return Counter.builder("ingest.dropped").tag(REASON, reason).register(registry);
    }

    /** 死信计数：reason = queue_full / persist_failed */
    public Counter deadLetter(String reason) {
        return Counter.builder("ingest.deadletter").tag(REASON, reason).register(registry);
    }

    /** 注册队列深度 Gauge，按 worker 维度区分。 */
    public void registerQueueDepth(String worker, BlockingQueue<?> queue) {
        Gauge.builder("ingest.queue.depth", queue, BlockingQueue::size)
                .description("worker 队列当前深度")
                .tag("worker", worker)
                .register(registry);
    }
}