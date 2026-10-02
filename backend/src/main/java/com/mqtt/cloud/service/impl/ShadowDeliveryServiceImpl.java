package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.ShadowProperties;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.ShadowDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备上线补发分发实现（T-16 设计文档 §9.2）。
 * <p>
 * 只做「有 {@code QUEUED} 才动作」的轻量判定：先 {@link DeviceCommandService#countQueued(Long)}
 * 短路为 0 的设备，避免常态下无谓的补发写库；逐设备 try/catch 隔离，异常不影响其余设备与本批其它 fan-out。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowDeliveryServiceImpl implements ShadowDeliveryService {

    private static final String MESSAGE_DATA = "data";
    private static final String MESSAGE_HEARTBEAT = "heartbeat";

    private final DeviceCommandService deviceCommandService;
    private final ShadowProperties properties;

    @Override
    public void onIngest(List<ResolvedEvent> events) {
        if (events == null || events.isEmpty() || !properties.isResendEnabled()) {
            return;
        }
        Set<Long> deviceIds = onlineDeviceIds(events);
        if (deviceIds.isEmpty()) {
            return;
        }
        int batch = Math.max(1, properties.getResendBatchSize());
        for (Long deviceId : deviceIds) {
            try {
                if (deviceCommandService.countQueued(deviceId) <= 0) {
                    continue;
                }
                int delivered = deviceCommandService.flushQueued(deviceId, batch);
                if (delivered > 0) {
                    log.info("设备上线补发完成: deviceId={}, delivered={}", deviceId, delivered);
                }
            } catch (Exception e) {
                log.warn("设备上线补发失败，跳过本设备: deviceId={}", deviceId, e);
            }
        }
    }

    /** 取本批事件中「上报 data / heartbeat」的设备 ID（去重）；这两类消息即设备在线的信号。 */
    private Set<Long> onlineDeviceIds(List<ResolvedEvent> events) {
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (ResolvedEvent event : events) {
            if (event == null || event.device() == null || event.record() == null) {
                continue;
            }
            String messageType = event.record().messageType();
            if (!MESSAGE_DATA.equals(messageType) && !MESSAGE_HEARTBEAT.equals(messageType)) {
                continue;
            }
            if (event.device().getId() != null) {
                deviceIds.add(event.device().getId());
            }
        }
        return deviceIds;
    }
}
