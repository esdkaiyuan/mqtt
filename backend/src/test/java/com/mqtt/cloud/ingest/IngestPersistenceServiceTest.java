package com.mqtt.cloud.ingest;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceStatus;
import com.mqtt.cloud.entity.Message;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DeviceStatusHistoryMapper;
import com.mqtt.cloud.mapper.MessageMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class IngestPersistenceServiceTest {

    private final DeviceMapper deviceMapper = mock(DeviceMapper.class);
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final DeviceStatusHistoryMapper statusHistoryMapper = mock(DeviceStatusHistoryMapper.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IngestMetrics metrics = new IngestMetrics(registry);

    private final IngestPersistenceService service =
            new IngestPersistenceService(deviceMapper, messageMapper, statusHistoryMapper, metrics);

    private Device device(long id, String key, String status) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey(key);
        device.setStatus(status);
        return device;
    }

    private IngestRecord record(String key, String messageType, LocalDateTime receivedAt) {
        return new IngestRecord(key, "device/" + key + "/" + messageType, messageType, "{}", 0, receivedAt);
    }

    private double counter(String name, String reason) {
        Counter counter = registry.find(name).tag("reason", reason).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void persist_should_batch_query_devices_and_drop_unknown() {
        when(deviceMapper.findByDeviceKeys(anyCollection())).thenReturn(List.of(
                device(1L, "k1", DeviceStatusValue.OFFLINE),
                device(2L, "k2", DeviceStatusValue.OFFLINE)));

        LocalDateTime now = LocalDateTime.now();
        List<ResolvedEvent> events = service.persist(List.of(
                record("k1", "data", now),
                record("k2", "data", now),
                record("k3", "data", now)));

        assertThat(events).hasSize(2);

        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(deviceMapper, times(1)).findByDeviceKeys(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder("k1", "k2", "k3");

        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageMapper, times(1)).insertBatch(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).hasSize(2);

        assertThat(counter("ingest.dropped", "unknown_device")).isEqualTo(1.0);
    }

    @Test
    void persist_should_write_history_only_for_status_change() {
        when(deviceMapper.findByDeviceKeys(anyCollection())).thenReturn(List.of(
                device(1L, "k1", DeviceStatusValue.OFFLINE),
                device(2L, "k2", DeviceStatusValue.ONLINE)));

        LocalDateTime now = LocalDateTime.now();
        service.persist(List.of(
                record("k1", "data", now),
                record("k2", "heartbeat", now)));

        ArgumentCaptor<List<DeviceStatus>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(statusHistoryMapper, times(1)).insertBatch(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
                .extracting(DeviceStatus::getDeviceId)
                .containsExactly(1L);
    }

    @Test
    void persist_should_skip_history_when_status_unchanged() {
        when(deviceMapper.findByDeviceKeys(anyCollection())).thenReturn(List.of(
                device(1L, "k1", DeviceStatusValue.ONLINE)));

        service.persist(List.of(record("k1", "data", LocalDateTime.now())));

        verify(statusHistoryMapper, never()).insertBatch(any());
    }
}