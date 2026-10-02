package com.mqtt.cloud.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.WebhookConfig;
import com.mqtt.cloud.service.CommandReplyService;
import com.mqtt.cloud.service.RealtimeBroadcaster;
import com.mqtt.cloud.service.ShadowDeliveryService;
import com.mqtt.cloud.service.ThingModelInterpretService;
import com.mqtt.cloud.service.WebhookConfigService;
import com.mqtt.cloud.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 落库成功后的下游分发：Webhook 事件、实时推送与物模型解析。
 * <p>
 * 由 worker 在落库事务提交后调用，保证「推送出去的，库里一定已经有」。
 * 实时推送经 {@link RealtimeBroadcaster} 出口，多副本时走 Redis 广播；
 * Webhook 配置按整批涉及用户一次性取回，替代原先的逐事件查询。
 * 物模型解析作为第三路 fan-out、命令回执作为第四路 fan-out、上线补发作为第五路 fan-out，
 * 逐条隔离失败，绝不让异常冒泡（否则 worker 会整批重试导致消息重复落库）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestDispatcher {

    private static final String TOPIC_HEARTBEAT = "heartbeat";
    private static final String TOPIC_LWT = "lwt";

    private static final String EVENT_DATA = "device.data";
    private static final String EVENT_HEARTBEAT = "device.heartbeat";
    private static final String EVENT_LWT = "device.lwt";

    private static final int ACTIVE = 1;

    private final WebhookDispatcher webhookDispatcher;
    private final WebhookConfigService webhookConfigService;
    private final RealtimeBroadcaster realtimeBroadcaster;
    private final ThingModelInterpretService thingModelInterpretService;
    private final CommandReplyService commandReplyService;
    private final ShadowDeliveryService shadowDeliveryService;

    public void dispatch(List<ResolvedEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        Map<Long, List<WebhookConfig>> webhooksByOwner = loadActiveWebhooksByOwner(events);
        for (ResolvedEvent event : events) {
            Device device = event.device();
            IngestRecord record = event.record();
            try {
                webhookDispatcher.dispatch(matchWebhooks(webhooksByOwner, device),
                        resolveEventType(record.messageType()), device, record.payload());
            } catch (Exception e) {
                log.warn("Webhook 事件分发失败: topic={}", record.topic(), e);
            }
            try {
                realtimeBroadcaster.broadcast(device, record.topic(), record.payload());
            } catch (Exception e) {
                log.warn("推送实时数据失败: topic={}", record.topic(), e);
            }
        }
        try {
            thingModelInterpretService.interpret(events);
        } catch (Exception e) {
            log.warn("物模型解析分发失败，跳过本批解析: size={}", events.size(), e);
        }
        try {
            commandReplyService.handle(events);
        } catch (Exception e) {
            log.warn("命令回执分发失败，跳过本批回执: size={}", events.size(), e);
        }
        try {
            shadowDeliveryService.onIngest(events);
        } catch (Exception e) {
            log.warn("影子补发分发失败，跳过本批补发: size={}", events.size(), e);
        }
    }

    /** 整批一次查询：取回本批事件涉及用户的全部启用中 Webhook，按用户分组。 */
    private Map<Long, List<WebhookConfig>> loadActiveWebhooksByOwner(List<ResolvedEvent> events) {
        List<Long> ownerIds = events.stream()
                .map(event -> event.device().getOwnerId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WebhookConfig::getUserId, ownerIds)
                .eq(WebhookConfig::getIsActive, ACTIVE);
        return webhookConfigService.list(wrapper).stream()
                .collect(Collectors.groupingBy(WebhookConfig::getUserId));
    }

    /** 等价于 getActiveWebhooksForDevice：设备级 Webhook（device_id 为空）或精确匹配本设备。 */
    private List<WebhookConfig> matchWebhooks(Map<Long, List<WebhookConfig>> webhooksByOwner, Device device) {
        List<WebhookConfig> candidates = webhooksByOwner.get(device.getOwnerId());
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(webhook -> webhook.getDeviceId() == null || webhook.getDeviceId().equals(device.getId()))
                .toList();
    }

    private String resolveEventType(String messageType) {
        return switch (messageType) {
            case TOPIC_HEARTBEAT -> EVENT_HEARTBEAT;
            case TOPIC_LWT -> EVENT_LWT;
            default -> EVENT_DATA;
        };
    }
}