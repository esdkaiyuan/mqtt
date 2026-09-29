package com.mqtt.cloud.ingest;

import com.mqtt.cloud.service.IngestDeadLetterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class IngestPipelineTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IngestMetrics metrics = new IngestMetrics(registry);
    private final IngestPersistenceService persistenceService = mock(IngestPersistenceService.class);
    private final IngestDispatcher dispatcher = mock(IngestDispatcher.class);
    private final IngestDeadLetterService deadLetterService = mock(IngestDeadLetterService.class);

    private IngestPipeline pipeline(int workerCount, int queueCapacity) {
        IngestProperties properties = new IngestProperties();
        properties.setWorkerCount(workerCount);
        properties.setQueueCapacity(queueCapacity);
        properties.setOfferTimeoutMs(20L);
        return new IngestPipeline(properties, metrics, persistenceService, dispatcher, deadLetterService);
    }

    private IngestRecord record(String deviceKey) {
        return new IngestRecord(deviceKey, "device/" + deviceKey + "/data", "data", "{}", 0, LocalDateTime.now());
    }

    private double counter(String name, String reason) {
        Counter counter = registry.find(name).tag("reason", reason).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void routeIndex_should_be_stable_and_in_range_for_same_device() {
        IngestPipeline pipeline = pipeline(4, 100);

        int first = pipeline.routeIndex("device-a");
        assertThat(pipeline.routeIndex("device-a")).isEqualTo(first);
        assertThat(first).isBetween(0, pipeline.workerCount() - 1);
    }

    @Test
    void submit_should_not_throw_and_count_drop_when_queue_full() {
        IngestPipeline pipeline = pipeline(1, 1);

        assertThatCode(() -> {
            pipeline.submit(record("device-a"));
            pipeline.submit(record("device-a"));
        }).doesNotThrowAnyException();

        assertThat(counter("ingest.dropped", "queue_full")).isEqualTo(1.0);
        verify(deadLetterService, times(1)).record(any(), eq("queue_full"), any());
    }

    @Test
    void submit_should_stop_enqueuing_after_stop() {
        IngestPipeline pipeline = pipeline(1, 100);

        pipeline.stop();
        pipeline.submit(record("device-a"));

        assertThat(counter("ingest.dropped", "queue_full")).isEqualTo(1.0);
        verify(deadLetterService, times(1)).record(any(), eq("queue_full"), any());
    }
}