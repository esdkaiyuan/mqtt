package com.mqtt.cloud.service;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationChannel;
import com.mqtt.cloud.service.notification.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * 通知统一分发器（T-27 设计文档 §4.4 / §8.1）。
 * <p>
 * 告警触发 / 恢复 / 升级的唯一分发入口：把一次事件组装为渠道无关的 {@link NotificationMessage}，
 * 按固定顺序（站内信 → 内置 Webhook → 邮件 → 短信）逐渠道投递，并写投递记录。
 * <p>
 * 逐渠道隔离：任一渠道抛异常只记 WARN 并写 {@code FAILED} 记录，不影响其他渠道，也不冒泡到告警评估链路。
 * 事件未匹配（渠道返回 {@link ChannelSendResult#skipped()}，或配置型渠道无命中配置行）不写记录，
 * 避免投递表被无关事件撑大。
 * <p>
 * 内置通道（{@code INAPP} / {@code WEBHOOK}）同步投递：站内信必达需在事件返回前落库；
 * 配置型通道（{@code EMAIL} / {@code SMS}）逐配置行在投递线程池上异步投递，避免网络耗时占用告警评估线程。
 * <p>
 * {@code app.notification.enabled=false} 时只保留内置 {@code WEBHOOK} 通道（T-17 既有行为在任何配置下原样成立），
 * 站内信、邮件、短信与升级能力停用；各渠道自身的开关（inapp / email / sms-enabled）由渠道实现判定。
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private static final List<String> CHANNEL_ORDER = List.of(
            NotificationConstants.CHANNEL_INAPP,
            NotificationConstants.CHANNEL_WEBHOOK,
            NotificationConstants.CHANNEL_EMAIL,
            NotificationConstants.CHANNEL_SMS);

    private final List<NotificationChannel> channels;
    private final NotificationProperties properties;
    private final NotificationDeliveryService deliveryService;
    private final NotificationChannelConfigService configService;
    private final ObjectMapper objectMapper;
    private final Executor deliveryExecutor;

    public NotificationDispatcher(List<NotificationChannel> channels,
                                  NotificationProperties properties,
                                  NotificationDeliveryService deliveryService,
                                  NotificationChannelConfigService configService,
                                  ObjectMapper objectMapper,
                                  @Qualifier("notificationDeliveryExecutor") Executor deliveryExecutor) {
        this.channels = channels.stream()
                .sorted(Comparator.comparingInt(channel -> orderOf(channel.channelType())))
                .toList();
        this.properties = properties;
        this.deliveryService = deliveryService;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.deliveryExecutor = deliveryExecutor;
    }

    /**
     * 分发一次通知事件。
     *
     * @param userId    归属用户，站内信 / 投递记录按此隔离
     * @param eventType 事件名，如 {@code alert.triggered}
     * @param device    关联设备；为空时既有 Webhook 通道跳过（站内信仍可投递）
     * @param record    告警记录，提供级别 / 标题 / 关联对象
     * @param payload   既有 Webhook 信封的载荷（T-17 载荷字段），序列化后作为 {@code payloadJson}
     */
    public void dispatch(Long userId, String eventType, Device device, AlertRecord record, Map<String, Object> payload) {
        dispatch(userId, eventType, device, record, payload, null);
    }

    /**
     * 分发一次通知事件，并可限定配置型渠道的投递目标（T-27 设计文档 §5.1 升级通知）。
     * <p>
     * {@code onlyChannelIds} 非空时，{@code EMAIL} / {@code SMS} 仅投递其中指定的渠道行，
     * 用于把升级通知精确投到策略指定的渠道；为空则按事件订阅匹配（与五参重载一致）。
     * 内置 {@code INAPP} / {@code WEBHOOK} 通道不受该参数影响：站内信必达、Webhook 保持既有语义。
     */
    public void dispatch(Long userId, String eventType, Device device, AlertRecord record,
                         Map<String, Object> payload, Set<Long> onlyChannelIds) {
        if (userId == null || record == null || eventType == null) {
            return;
        }
        NotificationMessage message = buildMessage(userId, eventType, device, record, payload);
        for (NotificationChannel channel : channels) {
            if (!active(channel.channelType())) {
                continue;
            }
            if (NotificationConstants.CONFIGURED_CHANNELS.contains(channel.channelType())) {
                dispatchConfigured(channel, message, onlyChannelIds);
            } else {
                deliver(channel, message, null);
            }
        }
    }

    /**
     * 配置型通道（EMAIL / SMS）逐配置行异步投递。
     * <p>
     * 每行一个投递任务并落到投递线程池上，避免网络耗时占用告警评估线程；行内 {@code channelId}
     * 写进消息副本，使渠道与重试都能精确定位到原配置行（而不是「用户后来新增的其它行」）。
     * 行间彼此隔离：单行失败只写自己的 {@code FAILED} 记录，不影响同渠道其余行。
     * <p>
     * 无命中配置行时不写记录（与「事件未匹配」同口径），避免投递表被无关事件撑大。
     *
     * @param onlyChannelIds 非空时仅投递其中指定的渠道行；为空按事件订阅匹配
     */
    private void dispatchConfigured(NotificationChannel channel, NotificationMessage message, Set<Long> onlyChannelIds) {
        List<NotificationChannelConfig> rows;
        try {
            rows = configService.listActiveMatching(message.getUserId(), channel.channelType(), message.getEventType());
        } catch (Exception e) {
            log.warn("通知渠道匹配失败，跳过: channel={}, event={}, userId={}",
                    channel.channelType(), message.getEventType(), message.getUserId(), e);
            return;
        }
        if (onlyChannelIds != null) {
            rows = rows.stream().filter(row -> onlyChannelIds.contains(row.getId())).toList();
        }
        for (NotificationChannelConfig row : rows) {
            NotificationMessage perRow = copyForChannel(message, row.getId());
            try {
                deliveryExecutor.execute(() -> deliver(channel, perRow, row.getId()));
            } catch (Exception e) {
                // 线程池已关闭等极端情形：兜底同步投递，避免告警评估链路丢事件
                log.warn("通知投递任务提交失败，改为同步投递: channel={}, channelId={}",
                        channel.channelType(), row.getId(), e);
                deliver(channel, perRow, row.getId());
            }
        }
    }

    private void deliver(NotificationChannel channel, NotificationMessage message, Long channelId) {
        ChannelSendResult result;
        try {
            result = channel.send(message);
        } catch (Exception e) {
            log.warn("通知渠道投递异常，已隔离: channel={}, event={}, userId={}",
                    channel.channelType(), message.getEventType(), message.getUserId(), e);
            result = ChannelSendResult.failure(null, e.getMessage());
        }
        if (result == null || result.skip()) {
            return;
        }
        try {
            deliveryService.record(message, channel.channelType(), channelId, result);
        } catch (Exception e) {
            log.warn("投递记录写入失败，忽略: channel={}, event={}", channel.channelType(), message.getEventType(), e);
        }
    }

    /** 为单个配置行复制消息并钉上 {@code channelId}，避免多行共享同一实例时相互覆盖。 */
    private static NotificationMessage copyForChannel(NotificationMessage source, Long channelId) {
        NotificationMessage copy = new NotificationMessage();
        copy.setUserId(source.getUserId());
        copy.setEventType(source.getEventType());
        copy.setChannelId(channelId);
        copy.setLevel(source.getLevel());
        copy.setTitle(source.getTitle());
        copy.setContent(source.getContent());
        copy.setRefType(source.getRefType());
        copy.setRefId(source.getRefId());
        copy.setDevice(source.getDevice());
        copy.setPayloadJson(source.getPayloadJson());
        return copy;
    }

    private boolean active(String channelType) {
        if (NotificationConstants.CHANNEL_WEBHOOK.equals(channelType)) {
            return true;
        }
        return properties.isEnabled() && NotificationConstants.CHANNELS.contains(channelType);
    }

    private NotificationMessage buildMessage(Long userId, String eventType, Device device,
                                             AlertRecord record, Map<String, Object> payload) {
        NotificationMessage message = new NotificationMessage();
        message.setUserId(userId);
        message.setEventType(eventType);
        message.setLevel(record.getSeverity());
        message.setTitle(record.getTitle());
        message.setContent(buildContent(eventType, record, device));
        message.setRefType(NotificationConstants.REF_TYPE_ALERT);
        message.setRefId(record.getId());
        message.setDevice(device);
        message.setPayloadJson(serialize(payload));
        return message;
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("通知载荷序列化失败，Webhook 通道将无 payload: event={}", payload.get("alertId"), e);
            return null;
        }
    }

    private static String buildContent(String eventType, AlertRecord record, Device device) {
        String deviceName = device != null
                ? (device.getDeviceName() != null ? device.getDeviceName() : device.getDeviceKey())
                : "未知设备";
        if (AlertConstants.EVENT_ALERT_RECOVERED.equals(eventType)) {
            return "设备「" + deviceName + "」的告警「" + record.getTitle() + "」已恢复";
        }
        StringBuilder content = new StringBuilder("设备「").append(deviceName).append("」触发告警「")
                .append(record.getTitle()).append("」");
        if (record.getIdentifier() != null) {
            content.append("，指标 ").append(record.getIdentifier());
        }
        if (record.getTriggerValue() != null) {
            content.append(" = ").append(record.getTriggerValue());
        }
        if (record.getTriggerCount() != null && record.getTriggerCount() > 1) {
            content.append("，累计 ").append(record.getTriggerCount()).append(" 次");
        }
        return content.toString();
    }

    private static int orderOf(String channelType) {
        int index = CHANNEL_ORDER.indexOf(channelType);
        return index < 0 ? CHANNEL_ORDER.size() : index;
    }
}