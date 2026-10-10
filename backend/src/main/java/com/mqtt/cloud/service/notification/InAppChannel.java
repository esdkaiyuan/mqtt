package com.mqtt.cloud.service.notification;

import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内信通道（内置必达通道，T-27 设计文档 §4.2）。
 * <p>
 * 落库即达，不做外部端点调用；由 {@code app.notification.inapp-enabled} 控制。
 * 写入与投递记录同事务边界外：本通道只负责落 {@code notification}（必达），
 * 投递记录由分发器在返回结果后尽力而为地写入，后者失败不影响前者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    private final NotificationService notificationService;
    private final NotificationProperties properties;

    @Override
    public String channelType() {
        return NotificationConstants.CHANNEL_INAPP;
    }

    @Override
    public ChannelSendResult send(NotificationMessage message) {
        if (!properties.isInappEnabled()) {
            return ChannelSendResult.skipped();
        }
        if (message.getUserId() == null) {
            return ChannelSendResult.skipped();
        }
        notificationService.create(message);
        return ChannelSendResult.success(null);
    }
}