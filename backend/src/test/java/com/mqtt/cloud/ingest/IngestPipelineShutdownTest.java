package com.mqtt.cloud.ingest;

import com.mqtt.cloud.service.IngestDeadLetterService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 停机排空：{@code @PreDestroy} 必须把队列里未落库的消息处理完，
 * 排空超时才转入死信，避免容器被强杀时静默丢消息。
 */
class IngestPipelineShutdownTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IngestMetrics metrics = new IngestMetrics(registry);
    private final IngestPersistenceService persistenceService = mock(IngestPersistenceService.class);
    private final IngestDispatcher dispatcher = mock(IngestDispatcher.class);
    private final IngestDeadLetterService deadLetterService = mock(IngestDeadLetterService.class);

    @Test
    void stop_should_drain_every_queued_record_to_persistence() {
        when(persistenceService.persist(anyList())).thenAnswer(invocation -> {
            List<IngestRecord> batch = invocation.getArgument(0);
            return batch.stream().map(record -> new ResolvedEvent(null, record)).toList();
        });

        IngestProperties properties = properties(2, 5_000L);
        IngestPipeline pipeline = new IngestPipeline(properties, metrics, persistenceService, dispatcher, deadLetterService);

        pipeline.start();
        for (int i = 0; i < 50; i++) {
            pipeline.submit(record("device-" + (i % 5)));
        }
        pipeline.stop();

        assertThat(registry.find("ingest.persisted").counter().count()).isEqualTo(50.0);
        verify(deadLetterService, never()).recordBatch(anyList(), any(), any());
        verify(deadLetterService, never()).record(any(), any(), any());
    }

    @Test
    void stop_should_route_leftover_to_dead_letter_when_drain_times_out() throws Exception {
        CountDownLatch persistEntered = new CountDownLatch(1);
        CountDownLatch releasePersist = new CountDownLatch(1);
        when(persistenceService.persist(anyList())).thenAnswer(invocation -> {
            persistEntered.countDown();
            releasePersist.await(10, TimeUnit.SECONDS);
            return List.of();
        });

        IngestProperties properties = properties(1, 200L);
        IngestPipeline pipeline = new IngestPipeline(properties, metrics, persistenceService, dispatcher, deadLetterService);

        pipeline.start();
        // 第一条让 worker 卡在 persist 上，后续消息只能留在队列里
        pipeline.submit(record("device-a"));
        assertThat(persistEntered.await(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 5; i++) {
            pipeline.submit(record("device-a"));
        }

        pipeline.stop();

        verify(deadLetterService).recordBatch(anyList(), any(), any());
        releasePersist.countDown();
    }

    private IngestProperties properties(int workerCount, long drainTimeoutMs) {
        IngestProperties properties = new IngestProperties();
        properties.setWorkerCount(workerCount);
        properties.setQueueCapacity(100);
        properties.setOfferTimeoutMs(20L);
        properties.setShutdownDrainTimeoutMs(drainTimeoutMs);
        return properties;
    }

    private IngestRecord record(String deviceKey) {
        return new IngestRecord(deviceKey, "device/" + deviceKey + "/data", "data", "{}", 0, LocalDateTime.now());
    }
}