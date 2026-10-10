package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.NotificationDelivery;
import com.mqtt.cloud.mapper.NotificationDeliveryMapper;
import com.mqtt.cloud.service.NotificationDeliveryService;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知投递记录服务实现（T-27 设计文档 §6.3 / §8.2）。
 * <p>
 * 只落摘要不落完整请求体，避免把渠道凭据写进日志表；失败按退避基数排一次重试锚点，
 * 后续顺延、终态判定与保留清理由 {@code NotificationSweeper} 驱动。
 */
@Service
public class NotificationDeliveryServiceImpl extends ServiceImpl<NotificationDeliveryMapper, NotificationDelivery>
        implements NotificationDeliveryService {

    private static final int MAX_TARGET_LENGTH = 255;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_ERROR_LENGTH = 500;
    /** 退避指数上限：防止配置异常导致 2^n 溢出或退避时间失真。 */
    private static final int MAX_BACKOFF_EXPONENT = 16;

    private final NotificationProperties properties;

    public NotificationDeliveryServiceImpl(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public NotificationDelivery record(NotificationMessage message, String channelType, Long channelId,
                                       ChannelSendResult result) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setUserId(message.getUserId());
        delivery.setChannelId(channelId);
        delivery.setChannelType(channelType);
        delivery.setEventType(message.getEventType());
        delivery.setTarget(truncate(result.target(), MAX_TARGET_LENGTH));
        delivery.setTitle(truncate(message.getTitle(), MAX_TITLE_LENGTH));
        delivery.setAttemptCount(1);

        if (result.success()) {
            delivery.setStatus(NotificationConstants.DELIVERY_SUCCESS);
        } else {
            delivery.setStatus(NotificationConstants.DELIVERY_FAILED);
            delivery.setErrorMessage(truncate(result.errorMessage(), MAX_ERROR_LENGTH));
            delivery.setNextAttemptAt(nextRetryAt(1, properties.getDelivery().getMaxAttempts(),
                    properties.getDelivery().getBackoffSeconds(), LocalDateTime.now()));
        }
        save(delivery);
        return delivery;
    }

    @Override
    public List<NotificationDelivery> retryDue(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        LambdaQueryWrapper<NotificationDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationDelivery::getStatus, NotificationConstants.DELIVERY_FAILED)
                .isNotNull(NotificationDelivery::getNextAttemptAt)
                .le(NotificationDelivery::getNextAttemptAt, LocalDateTime.now())
                .orderByAsc(NotificationDelivery::getNextAttemptAt)
                .orderByAsc(NotificationDelivery::getId);
        Page<NotificationDelivery> page = new Page<>(1, limit, false);
        return baseMapper.selectPage(page, wrapper).getRecords();
    }

    @Override
    public int purgeBefore(LocalDateTime cutoff) {
        if (cutoff == null) {
            return 0;
        }
        LambdaQueryWrapper<NotificationDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(NotificationDelivery::getCreatedAt, cutoff);
        return baseMapper.delete(wrapper);
    }

    @Override
    public void applyRetryResult(NotificationDelivery delivery, ChannelSendResult result) {
        if (delivery == null || delivery.getId() == null) {
            return;
        }
        int attempts = (delivery.getAttemptCount() == null ? 1 : delivery.getAttemptCount()) + 1;
        int maxAttempts = properties.getDelivery().getMaxAttempts();

        LambdaUpdateWrapper<NotificationDelivery> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(NotificationDelivery::getId, delivery.getId())
                .set(NotificationDelivery::getAttemptCount, attempts);

        if (result != null && result.success()) {
            wrapper.set(NotificationDelivery::getStatus, NotificationConstants.DELIVERY_SUCCESS)
                    .set(NotificationDelivery::getNextAttemptAt, null)
                    .set(NotificationDelivery::getErrorMessage, null);
            if (result.target() != null) {
                wrapper.set(NotificationDelivery::getTarget, truncate(result.target(), MAX_TARGET_LENGTH));
            }
        } else {
            String error = result == null ? "重投失败" : result.errorMessage();
            boolean stop = result == null || result.skip() || attempts >= maxAttempts;
            wrapper.set(NotificationDelivery::getStatus, NotificationConstants.DELIVERY_FAILED)
                    .set(NotificationDelivery::getErrorMessage, truncate(error, MAX_ERROR_LENGTH))
                    .set(NotificationDelivery::getNextAttemptAt, stop ? null
                            : nextRetryAt(attempts, maxAttempts,
                                    properties.getDelivery().getBackoffSeconds(), LocalDateTime.now()));
        }
        update(wrapper);
    }

    /**
     * 计算下一次重试时间：第 n 次尝试后顺延 {@code backoffSeconds × 2^(n-1)}；已达上限返回 {@code null}。
     * <p>包内可见以便单测直接验证退避顺延与达上限停止两条语义。
     */
    static LocalDateTime nextRetryAt(int attempts, int maxAttempts, int backoffSeconds, LocalDateTime now) {
        if (attempts >= maxAttempts) {
            return null;
        }
        int exponent = Math.min(Math.max(attempts - 1, 0), MAX_BACKOFF_EXPONENT);
        long delaySeconds = (long) backoffSeconds * (1L << exponent);
        return now.plusSeconds(delaySeconds);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}