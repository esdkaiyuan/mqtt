package com.mqtt.cloud.ingest;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceStatus;
import com.mqtt.cloud.entity.Message;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DeviceStatusHistoryMapper;
import com.mqtt.cloud.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批量落库：一次解析设备、一次插入消息、按设备聚合后批量更新状态。
 * <p>
 * 事务内只做 DB 操作；Webhook 与 SSE 推送由 {@link IngestDispatcher} 在事务提交后进行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestPersistenceService {

    private static final String DIRECTION_SUBSCRIBE = "SUBSCRIBE";
    private static final String TOPIC_DATA = "data";
    private static final String TOPIC_HEARTBEAT = "heartbeat";
    private static final String TOPIC_LWT = "lwt";

    private final DeviceMapper deviceMapper;
    private final MessageMapper messageMapper;
    private final DeviceStatusHistoryMapper statusHistoryMapper;
    private final IngestMetrics metrics;

    @Transactional(rollbackFor = Exception.class)
    public List<ResolvedEvent> persist(List<IngestRecord> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }

        Set<String> keys = batch.stream()
                .map(IngestRecord::deviceKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Device> devices = deviceMapper.findByDeviceKeys(keys).stream()
                .collect(Collectors.toMap(Device::getDeviceKey, Function.identity(), (first, second) -> first));

        List<IngestRecord> known = batch.stream()
                .filter(record -> devices.containsKey(record.deviceKey()))
                .toList();
        int unknown = batch.size() - known.size();
        if (unknown > 0) {
            metrics.dropped("unknown_device").increment(unknown);
        }
        if (known.isEmpty()) {
            return List.of();
        }

        messageMapper.insertBatch(known.stream()
                .map(record -> toMessage(devices.get(record.deviceKey()), record))
                .toList());
        updateDeviceStates(known, devices);

        return known.stream()
                .map(record -> new ResolvedEvent(devices.get(record.deviceKey()), record))
                .toList();
    }

    /**
     * 按 deviceKey 聚合到一条：取 receivedAt 最大者决定目标状态，避免同批内互相覆盖。
     * 状态历史只在状态真正变化时写入，与改造前的语义一致。
     */
    private void updateDeviceStates(List<IngestRecord> known, Map<String, Device> devices) {
        Map<Long, IngestRecord> latestByDevice = new LinkedHashMap<>();
        for (IngestRecord record : known) {
            Long deviceId = devices.get(record.deviceKey()).getId();
            IngestRecord previous = latestByDevice.get(deviceId);
            if (previous == null || record.receivedAt().isAfter(previous.receivedAt())) {
                latestByDevice.put(deviceId, record);
            }
        }

        List<Device> updates = new ArrayList<>(latestByDevice.size());
        List<DeviceStatus> histories = new ArrayList<>();
        for (IngestRecord record : latestByDevice.values()) {
            Device device = devices.get(record.deviceKey());
            String target = resolveStatus(record.messageType());
            if (target == null) {
                continue;
            }
            boolean online = DeviceStatusValue.ONLINE.equals(target);
            boolean changed = !target.equals(device.getStatus());

            Device update = new Device();
            update.setId(device.getId());
            update.setStatus(target);
            // OFFLINE 不改 last_seen，沿用库中现值，避免批量更新把在线时间抹掉
            update.setLastSeen(online ? record.receivedAt() : device.getLastSeen());
            updates.add(update);

            if (online) {
                device.setLastSeen(record.receivedAt());
            }
            if (changed) {
                device.setStatus(target);
                DeviceStatus history = new DeviceStatus();
                history.setDeviceId(device.getId());
                history.setStatus(target);
                history.setTimestamp(record.receivedAt());
                histories.add(history);
            }
        }

        if (!updates.isEmpty()) {
            deviceMapper.updateStatusBatch(updates);
        }
        if (!histories.isEmpty()) {
            statusHistoryMapper.insertBatch(histories);
        }
    }

    private String resolveStatus(String messageType) {
        return switch (messageType) {
            case TOPIC_DATA, TOPIC_HEARTBEAT -> DeviceStatusValue.ONLINE;
            case TOPIC_LWT -> DeviceStatusValue.OFFLINE;
            default -> null;
        };
    }

    private Message toMessage(Device device, IngestRecord record) {
        Message message = new Message();
        message.setTopic(record.topic());
        message.setDirection(DIRECTION_SUBSCRIBE);
        message.setPayload(record.payload());
        message.setQos(record.qos());
        message.setDeviceId(device.getId());
        message.setSentAt(record.receivedAt());
        return message;
    }
}