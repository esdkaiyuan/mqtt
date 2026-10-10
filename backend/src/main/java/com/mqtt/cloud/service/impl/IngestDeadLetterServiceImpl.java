package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.IngestDeadLetter;
import com.mqtt.cloud.ingest.IngestMetrics;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.mapper.IngestDeadLetterMapper;
import com.mqtt.cloud.service.IngestDeadLetterService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    /**
     * 延迟注入以打断循环依赖：IngestPipeline → IngestDeadLetterService（溢出兜底）
     * → IngestPipeline（重放重投）。重放是低频的管理动作，构造期不需要管线实例。
     */
    private final ObjectProvider<IngestPipeline> ingestPipelineProvider;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService writer;
    private final AtomicLong lastLogAt = new AtomicLong(0L);

    public IngestDeadLetterServiceImpl(IngestDeadLetterMapper deadLetterMapper,
                                       IngestMetrics metrics,
                                       ObjectProvider<IngestPipeline> ingestPipelineProvider,
                                       PlatformTransactionManager transactionManager) {
        this.deadLetterMapper = deadLetterMapper;
        this.metrics = metrics;
        this.ingestPipelineProvider = ingestPipelineProvider;
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

    @Override
    public boolean replay(Long id) {
        IngestDeadLetter row = deadLetterMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ResultCode.DEAD_LETTER_NOT_FOUND);
        }
        if (!IngestDeadLetter.STATUS_PENDING.equals(row.getStatus())) {
            // 终态不可覆盖：重复重放直接拒绝，避免同一条消息被反复投递造成数据翻倍
            throw new BusinessException(ResultCode.DEAD_LETTER_ALREADY_DISPOSED);
        }
        // 条件更新（WHERE status='PENDING'）：并发重放时只有一方能改成功
        int updated = deadLetterMapper.update(null, Wrappers.<IngestDeadLetter>lambdaUpdate()
                .eq(IngestDeadLetter::getId, id)
                .eq(IngestDeadLetter::getStatus, IngestDeadLetter.STATUS_PENDING)
                .set(IngestDeadLetter::getStatus, IngestDeadLetter.STATUS_REPLAYED)
                .setSql("attempts = attempts + 1"));
        if (updated == 0) {
            throw new BusinessException(ResultCode.DEAD_LETTER_ALREADY_DISPOSED);
        }

        // 重新投回摄取管线：submit 只做入队受理，worker 异步落库
        try {
            IngestPipeline pipeline = ingestPipelineProvider.getIfAvailable();
            if (pipeline == null) {
                throw new IllegalStateException("摄取管线未就绪");
            }
            pipeline.submit(toIngestRecord(row));
        } catch (Exception e) {
            // 管线满或已停机：回退状态为 PENDING，让运维稍后重试，避免消息静默丢失
            deadLetterMapper.update(null, Wrappers.<IngestDeadLetter>lambdaUpdate()
                    .eq(IngestDeadLetter::getId, id)
                    .set(IngestDeadLetter::getStatus, IngestDeadLetter.STATUS_PENDING)
                    .setSql("attempts = attempts + 1"));
            log.warn("死信重放入队失败，已回退为 PENDING: id={}, err={}", id, e.getMessage());
            throw new BusinessException(ResultCode.DEAD_LETTER_REPLAY_FAILED, "摄取队列不可用，请稍后重试");
        }
        log.info("死信已重放: id={}, deviceKey={}", id, row.getDeviceKey());
        return true;
    }

    @Override
    public boolean discard(Long id) {
        int updated = deadLetterMapper.update(null, Wrappers.<IngestDeadLetter>lambdaUpdate()
                .eq(IngestDeadLetter::getId, id)
                .eq(IngestDeadLetter::getStatus, IngestDeadLetter.STATUS_PENDING)
                .set(IngestDeadLetter::getStatus, IngestDeadLetter.STATUS_DISCARDED));
        if (updated == 0) {
            // 区分「不存在」与「已处置」，给运维可操作的提示
            if (deadLetterMapper.selectById(id) == null) {
                throw new BusinessException(ResultCode.DEAD_LETTER_NOT_FOUND);
            }
            throw new BusinessException(ResultCode.DEAD_LETTER_ALREADY_DISPOSED);
        }
        log.info("死信已丢弃: id={}", id);
        return true;
    }

    @Override
    public int replayBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int replayed = 0;
        for (Long id : ids) {
            try {
                replay(id);
                replayed++;
            } catch (BusinessException e) {
                // 逐条隔离：单条已处置 / 不可用不阻断其余条目
                log.warn("批量重放跳过 id={} reason={}", id, e.getMessage());
            }
        }
        log.info("批量重放完成: 请求={}, 成功={}", ids.size(), replayed);
        return replayed;
    }

    @Override
    public Map<String, Long> countByStatus() {
        // 只统计三态计数，避免把未知状态也带进来
        List<IngestDeadLetter> rows = deadLetterMapper.selectList(Wrappers.<IngestDeadLetter>lambdaQuery()
                .select(IngestDeadLetter::getStatus)
                .groupBy(IngestDeadLetter::getStatus));
        Map<String, Long> result = new HashMap<>();
        result.put(IngestDeadLetter.STATUS_PENDING, 0L);
        result.put(IngestDeadLetter.STATUS_REPLAYED, 0L);
        result.put(IngestDeadLetter.STATUS_DISCARDED, 0L);
        for (IngestDeadLetter row : rows) {
            if (row.getStatus() != null) {
                result.merge(row.getStatus(), 1L, Long::sum);
            }
        }
        return result;
    }

    /** 死信行还原为摄取载体：字段与 IngestRecord 一一对应。 */
    private IngestRecord toIngestRecord(IngestDeadLetter row) {
        return new IngestRecord(
                row.getDeviceKey(),
                row.getTopic(),
                row.getMessageType(),
                row.getPayload(),
                row.getQos() == null ? 1 : row.getQos(),
                row.getReceivedAt() == null ? LocalDateTime.now() : row.getReceivedAt(),
                // 重放不沿用原 traceId：重放是一次新的处理动作，
                // 独立 traceId 才能与「当初为何失败」的原链路日志区分开
                "replay-" + row.getId() + "-" + System.currentTimeMillis());
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