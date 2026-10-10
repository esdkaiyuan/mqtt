package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.NotificationDelivery;
import com.mqtt.cloud.service.NotificationDeliveryService;
import com.mqtt.cloud.service.NotificationSweeperService;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationChannel;
import com.mqtt.cloud.service.notification.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知投递巡检实现（T-27 设计文档 §8.2）。
 * <p>
 * 重投按投递记录上冗余的 {@code channel_type} / {@code channel_id} 还原消息并交回原渠道，
 * 因此重投精确落在原配置行上，不会漂移到用户后来新增的其它渠道；渠道配置已被删除时，
 * 渠道返回 {@code skipped}，巡检据 {@code applyRetryResult} 置终态，避免无限重试。
 * <p>
 * 投递记录不落正文，重投时正文回退为标题快照 —— 这是「不把凭据与完整请求体写进日志表」的
 * 代价，失败留痕仍可定位到事件与目标。
 * <p>
 * 单条异常只记 WARN，不影响同轮其余记录与保留清理。
 */
@Slf4j
@Service
public class NotificationSweeperServiceImpl implements NotificationSweeperService {

    private final NotificationDeliveryService deliveryService;
    private final NotificationProperties properties;
    private final Map<String, NotificationChannel> channelsByType;

    public NotificationSweeperServiceImpl(NotificationDeliveryService deliveryService,
                                          NotificationProperties properties,
                                          List<NotificationChannel> channels) {
        this.deliveryService = deliveryService;
        this.properties = properties;
        Map<String, NotificationChannel> indexed = new HashMap<>();
        for (NotificationChannel channel : channels) {
            indexed.put(channel.channelType(), channel);
        }
        this.channelsByType = Map.copyOf(indexed);
    }

    @Override
    public void sweep() {
        retryDue();
        purgeExpired();
    }

    private void retryDue() {
        List<NotificationDelivery> due;
        try {
            due = deliveryService.retryDue(properties.getDelivery().getBatchSize());
        } catch (Exception e) {
            log.warn("投递重试取待重试集失败", e);
            return;
        }
        for (NotificationDelivery delivery : due) {
            try {
                retryOne(delivery);
            } catch (Exception e) {
                log.warn("投递重试单条失败，跳过: deliveryId={}", delivery.getId(), e);
            }
        }
    }

    private void retryOne(NotificationDelivery delivery) {
        NotificationChannel channel = channelsByType.get(delivery.getChannelType());
        if (channel == null) {
            // 渠道类型已不可用（类型下线或未注册）：置终态，避免每条记录每轮都被反复捞起
            deliveryService.applyRetryResult(delivery,
                    ChannelSendResult.failure(null, "渠道类型不可用: " + delivery.getChannelType()));
            return;
        }
        ChannelSendResult result;
        try {
            result = channel.send(rebuild(delivery));
        } catch (Exception e) {
            result = ChannelSendResult.failure(null, e.getMessage());
        }
        deliveryService.applyRetryResult(delivery, result);
    }

    private NotificationMessage rebuild(NotificationDelivery delivery) {
        NotificationMessage message = new NotificationMessage();
        message.setUserId(delivery.getUserId());
        message.setEventType(delivery.getEventType());
        message.setChannelId(delivery.getChannelId());
        message.setTitle(delivery.getTitle());
        message.setContent(delivery.getTitle());
        return message;
    }

    private void purgeExpired() {
        int retentionDays = properties.getDelivery().getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        try {
            int purged = deliveryService.purgeBefore(LocalDateTime.now().minusDays(retentionDays));
            if (purged > 0) {
                log.info("清理过期投递记录 {} 条（保留 {} 天）", purged, retentionDays);
            }
        } catch (Exception e) {
            log.warn("投递记录保留清理失败", e);
        }
    }
}