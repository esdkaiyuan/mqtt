package com.mqtt.cloud.ingest;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.IngestDeadLetterService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class IngestWorkerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IngestMetrics metrics = new IngestMetrics(registry);
    private final IngestPersistenceService persistenceService = mock(IngestPersistenceService.class);
    private final IngestDispatcher dispatcher = mock(IngestDispatcher.class);
    private final IngestDeadLetterService deadLetterService = mock(IngestDeadLetterService.class);

    private final IngestRecord record =
            new IngestRecord("dev-1", "device/dev-1/data", "data", "{}", 0, LocalDateTime.now());

    private IngestProperties properties() {
        IngestProperties properties = new IngestProperties();
        properties.setBatchSize(10);
        properties.setFlushIntervalMs(20L);
        properties.setMaxAttempts(3);
        properties.setRetryBackoffMs(1L);
        return properties;
    }

    private IngestWorker worker(BlockingQueue<IngestRecord> queue) {
        return new IngestWorker(0, queue, properties(), persistenceService, dispatcher, deadLetterService, metrics);
    }

    @Test
    void persist_should_retry_then_move_batch_to_dead_letter() throws Exception {
        when(persistenceService.persist(anyList())).thenThrow(new IllegalStateException("db down"));

        BlockingQueue<IngestRecord> queue = new ArrayBlockingQueue<>(10);
        queue.put(record);
        IngestWorker worker = worker(queue);
        worker.start();

        ArgumentCaptor<List<IngestRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(deadLetterService, timeout(5000).times(1))
                .recordBatch(captor.capture(), eq("persist_failed"), any());
        assertThat(captor.getValue()).containsExactly(record);

        verify(persistenceService, times(3)).persist(anyList());
        verify(dispatcher, never()).dispatch(any());

        worker.shutdown();
        assertThat(worker.awaitTermination(2000)).isTrue();

        assertThat(registry.get("ingest.batch.failures").counter().count()).isEqualTo(1.0);
    }

    @Test
    void persist_should_dispatch_and_not_dead_letter_on_success() throws Exception {
        Device device = new Device();
        device.setId(1L);
        List<ResolvedEvent> events = List.of(new ResolvedEvent(device, record));
        when(persistenceService.persist(anyList())).thenReturn(events);

        BlockingQueue<IngestRecord> queue = new ArrayBlockingQueue<>(10);
        queue.put(record);
        IngestWorker worker = worker(queue);
        worker.start();

        verify(dispatcher, timeout(5000).times(1)).dispatch(events);
        verify(deadLetterService, never()).recordBatch(anyList(), any(), any());

        worker.shutdown();
        assertThat(worker.awaitTermination(2000)).isTrue();

        assertThat(registry.get("ingest.persisted").counter().count()).isEqualTo(1.0);
    }
}