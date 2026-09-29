package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.IngestDeadLetter;
import com.mqtt.cloud.ingest.IngestMetrics;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.mapper.IngestDeadLetterMapper;
import com.mqtt.cloud.service.IngestDeadLetterService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 死信兜底实现：异步落库 + 独立事务 + 限频告警。
 * <p>
 * 队列溢出发生在 MQTT 回调线程，落库失败发生在摄取 worker 线程。若在此同步写库，
 * 数据库不可用时会把这两个线程一起阻塞在连接超时上（回调线程阻塞可能触发 keepalive
 * 超时导致重连风暴）。因此写入交给专用单线程完成，两个调用方都立即返回。
 */
@Slf4j
@Service
public class IngestDeadLetterServiceImpl implements IngestDeadLetterService {

    private static final String REASON_WRITE_FAILED = "deadletter_write_failed";
    private static final int ERROR_MESSAGE_MAX_LEN = 500;

    /** 告警限频窗口，避免队列持续满时刷屏 */
    private static final long LOG_INTERVAL_MS = 5000L;

    private final IngestDeadLetterMapper deadLetterMapper;
    private final IngestMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService writer;
    private final AtomicLong lastLogAt = new AtomicLong(0L);

    public IngestDeadLetterServiceImpl(IngestDeadLetterMapper deadLetterMapper,
                                       IngestMetrics metrics,
                                       PlatformTransactionManager transactionManager) {
        this.deadLetterMapper = deadLetterMapper;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ingest-dead-letter");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void record(IngestRecord record, String reason, String errorMessage) {
        recordBatch(List.of(record), reason, errorMessage);
    }

    @Override
    public void recordBatch(List<IngestRecord> records, String reason, String errorMessage) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<IngestDeadLetter> rows = records.stream()
                .map(record -> toEntity(record, reason, errorMessage))
                .toList();
        writer.execute(() -> persist(rows, reason));
    }

    @Override
    public IPage<IngestDeadLetter> page(String status, int pageNum, int pageSize) {
        Page<IngestDeadLetter> page = new Page<>(pageNum, pageSize);
        return deadLetterMapper.selectPage(page, Wrappers.<IngestDeadLetter>lambdaQuery()
                .eq(StringUtils.hasText(status), IngestDeadLetter::getStatus, status)
                .orderByDesc(IngestDeadLetter::getCreatedAt));
    }

    private void persist(List<IngestDeadLetter> rows, String reason) {
        try {
            transactionTemplate.executeWithoutResult(status -> deadLetterMapper.insertBatch(rows));
            metrics.deadLetter(reason).increment(rows.size());
            if (shouldLog()) {
                log.error("消息转入死信: reason={}, size={}, deviceKeys={}", reason, rows.size(), sample(rows));
            }
        } catch (Exception e) {
            // 死信自身失败只能记日志与指标，绝不能向上抛
            log.error("写入死信失败，丢失 {} 条消息: reason={}, deviceKey={}", rows.size(), reason,
                    rows.isEmpty() ? "-" : rows.get(0).getDeviceKey(), e);
            metrics.deadLetter(REASON_WRITE_FAILED).increment(rows.size());
        }
    }

    private IngestDeadLetter toEntity(IngestRecord record, String reason, String errorMessage) {
        IngestDeadLetter entity = new IngestDeadLetter();
        entity.setDeviceKey(record.deviceKey());
        entity.setTopic(record.topic());
        entity.setMessageType(record.messageType());
        entity.setPayload(record.payload());
        entity.setQos(record.qos());
        entity.setReceivedAt(record.receivedAt());
        entity.setReason(reason);
        entity.setAttempts(0);
        entity.setErrorMessage(truncate(errorMessage));
        entity.setStatus(IngestDeadLetter.STATUS_PENDING);
        return entity;
    }

    private String truncate(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() <= ERROR_MESSAGE_MAX_LEN
                ? errorMessage
                : errorMessage.substring(0, ERROR_MESSAGE_MAX_LEN);
    }

    private String sample(List<IngestDeadLetter> rows) {
        return rows.stream()
                .limit(5)
                .map(IngestDeadLetter::getDeviceKey)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
    }

    private boolean shouldLog() {
        long now = System.currentTimeMillis();
        long last = lastLogAt.get();
        return now - last >= LOG_INTERVAL_MS && lastLogAt.compareAndSet(last, now);
    }

    @PreDestroy
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}