package com.mqtt.cloud.ingest;

import com.mqtt.cloud.service.IngestDeadLetterService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 摄取 worker：单线程消费自己的队列，按 batch-size / flush-interval-ms 双阈值攒批落库。
 * <p>
 * 同一 deviceKey 恒定路由到同一 worker（见 {@link IngestPipeline#routeIndex}），因此单设备在库内的写入顺序与到达顺序一致。
 */
@Slf4j
public class IngestWorker {

    private static final String REASON_PERSIST_FAILED = "persist_failed";

    private final int index;
    private final BlockingQueue<IngestRecord> queue;
    private final IngestProperties properties;
    private final IngestPersistenceService persistenceService;
    private final IngestDispatcher dispatcher;
    private final IngestDeadLetterService deadLetterService;
    private final IngestMetrics metrics;

    private volatile boolean running;
    private Thread thread;

    IngestWorker(int index,
                 BlockingQueue<IngestRecord> queue,
                 IngestProperties properties,
                 IngestPersistenceService persistenceService,
                 IngestDispatcher dispatcher,
                 IngestDeadLetterService deadLetterService,
                 IngestMetrics metrics) {
        this.index = index;
        this.queue = queue;
        this.properties = properties;
        this.persistenceService = persistenceService;
        this.dispatcher = dispatcher;
        this.deadLetterService = deadLetterService;
        this.metrics = metrics;
    }

    void start() {
        running = true;
        thread = new Thread(this::loop, "ingest-worker-" + index);
        thread.setDaemon(true);
        thread.start();
    }

    /** 停止接收新批次，已入队的消息仍会被处理完。 */
    void shutdown() {
        running = false;
    }

    boolean awaitTermination(long timeoutMs) throws InterruptedException {
        if (thread == null) {
            return true;
        }
        thread.join(timeoutMs);
        return !thread.isAlive();
    }

    int index() {
        return index;
    }

    int depth() {
        return queue.size();
    }

    void drainTo(List<IngestRecord> target) {
        queue.drainTo(target);
    }

    private void loop() {
        List<IngestRecord> batch = new ArrayList<>(properties.getBatchSize());
        try {
            while (running || !queue.isEmpty()) {
                IngestRecord first = queue.poll(properties.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, Math.max(0, properties.getBatchSize() - 1));
                persist(batch);
                batch.clear();
            }
            // 停机：把 running 置 false 之后仍留在队列里的消息落完
            queue.drainTo(batch);
            if (!batch.isEmpty()) {
                persist(batch);
                batch.clear();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("摄取 worker 被中断，剩余 {} 条未落库: worker={}", queue.size(), index);
        }
    }

    private void persist(List<IngestRecord> batch) {
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        int attempt = 0;
        while (true) {
            long startNanos = System.nanoTime();
            try {
                List<ResolvedEvent> events = persistenceService.persist(batch);
                metrics.persistDuration().record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                metrics.batchSize().record(batch.size());
                metrics.persisted().increment(events.size());
                // 事务已提交，推送与库内数据保持一致
                dispatcher.dispatch(events);
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    metrics.batchFailures().increment();
                    log.error("批量落库重试耗尽，整批转入死信: size={}, attempts={}", batch.size(), attempt, e);
                    deadLetterService.recordBatch(List.copyOf(batch), REASON_PERSIST_FAILED, describe(e));
                    return;
                }
                log.warn("批量落库失败，第 {} 次重试: size={}, error={}", attempt, batch.size(), e.getMessage());
                if (!sleep(properties.getRetryBackoffMs() * attempt)) {
                    return;
                }
            }
        }
    }

    private String describe(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }

    /** @return false 表示等待被中断，调用方应终止重试 */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}