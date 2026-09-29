package com.mqtt.cloud.ingest;

import com.mqtt.cloud.service.IngestDeadLetterService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 上行摄取管线：回调线程只做「路由 + 入队」，DB / HTTP 全部在 worker 线程完成。
 * <p>
 * 队列有界且不阻塞回调线程 —— 阻塞 Paho 的 {@code CommsCallback} 会连带阻塞 receiver 线程，
 * 可能触发 keepalive 超时导致重连风暴；因此溢出时走显式告警 + 死信兜底，而不是静默丢弃或阻塞。
 */
@Slf4j
@Component
public class IngestPipeline {

    private static final String REASON_QUEUE_FULL = "queue_full";

    private final IngestProperties properties;
    private final IngestMetrics metrics;
    private final IngestDeadLetterService deadLetterService;
    private final List<BlockingQueue<IngestRecord>> queues = new ArrayList<>();
    private final List<IngestWorker> workers = new ArrayList<>();

    /** stop() 后置 false，submit 直接走溢出分支，不再入队 */
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private volatile boolean started;

    public IngestPipeline(IngestProperties properties,
                          IngestMetrics metrics,
                          IngestPersistenceService persistenceService,
                          IngestDispatcher dispatcher,
                          IngestDeadLetterService deadLetterService) {
        this.properties = properties;
        this.metrics = metrics;
        this.deadLetterService = deadLetterService;

        int workerCount = Math.max(1, properties.getWorkerCount());
        int capacity = Math.max(1, properties.getQueueCapacity());
        for (int i = 0; i < workerCount; i++) {
            BlockingQueue<IngestRecord> queue = new ArrayBlockingQueue<>(capacity);
            queues.add(queue);
            workers.add(new IngestWorker(i, queue, properties, persistenceService, dispatcher, deadLetterService, metrics));
            metrics.registerQueueDepth(String.valueOf(i), queue);
        }
    }

    /** 回调线程调用：只做路由与入队，禁止任何 DB / HTTP 操作。 */
    public void submit(IngestRecord record) {
        metrics.submitted().increment();
        if (!accepting.get()) {
            onOverflow(record);
            return;
        }
        BlockingQueue<IngestRecord> queue = queues.get(routeIndex(record.deviceKey()));
        try {
            if (!queue.offer(record, properties.getOfferTimeoutMs(), TimeUnit.MILLISECONDS)) {
                onOverflow(record);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onOverflow(record);
        }
    }

    /** 同一 deviceKey 恒定映射到同一队列，保证单设备有序。 */
    int routeIndex(String deviceKey) {
        return Math.floorMod(deviceKey.hashCode(), queues.size());
    }

    int workerCount() {
        return workers.size();
    }

    private void onOverflow(IngestRecord record) {
        metrics.dropped(REASON_QUEUE_FULL).increment();
        deadLetterService.record(record, REASON_QUEUE_FULL, "摄取队列已满");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (started || !properties.isEnabled()) {
            return;
        }
        started = true;
        workers.forEach(IngestWorker::start);
        log.info("摄取管线已启动: workers={}, queueCapacity={}, batchSize={}, flushIntervalMs={}",
                workers.size(), properties.getQueueCapacity(), properties.getBatchSize(), properties.getFlushIntervalMs());
    }

    @PreDestroy
    public void stop() {
        accepting.set(false);
        if (!started) {
            return;
        }
        int backlog = workers.stream().mapToInt(IngestWorker::depth).sum();
        workers.forEach(IngestWorker::shutdown);

        long deadline = System.currentTimeMillis() + properties.getShutdownDrainTimeoutMs();
        int deadLettered = 0;
        for (IngestWorker worker : workers) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                deadLettered += drainToDeadLetter(worker);
                continue;
            }
            try {
                if (!worker.awaitTermination(remaining)) {
                    log.warn("摄取 worker 未在超时内排空，剩余消息转入死信: worker={}, depth={}",
                            worker.index(), worker.depth());
                    deadLettered += drainToDeadLetter(worker);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deadLettered += drainToDeadLetter(worker);
                break;
            }
        }
        log.info("摄取管线已停止: 待排空 {} 条，已排空 {} 条，转死信 {} 条",
                backlog, backlog - deadLettered, deadLettered);
    }

    /** @return 转入死信的条数 */
    private int drainToDeadLetter(IngestWorker worker) {
        List<IngestRecord> leftover = new ArrayList<>();
        worker.drainTo(leftover);
        if (leftover.isEmpty()) {
            return 0;
        }
        deadLetterService.recordBatch(leftover, "persist_failed", "停机排空超时");
        return leftover.size();
    }
}