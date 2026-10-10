package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.Notification;
import com.mqtt.cloud.mapper.NotificationMapper;
import com.mqtt.cloud.service.NotificationService;
import com.mqtt.cloud.service.notification.NotificationMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内信服务实现（T-27 设计文档 §6.1 / §10.1）。
 * <p>
 * 记录按 {@code user_id} 隔离，读写前先做归属校验；置已读以 {@code read_at IS NULL} 为条件，
 * 重复标记幂等。写入后按保留上限清理最旧的已读条目，避免站内信表无界增长。
 */
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TRIM_PER_CALL = 1000;

    private final NotificationProperties properties;

    public NotificationServiceImpl(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public IPage<Notification> page(Long userId, Boolean unreadOnly, Integer pageNum, Integer pageSize) {
        int effectiveNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int effectiveSize = (pageSize == null || pageSize < 1)
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.isNull(Notification::getReadAt);
        }
        wrapper.orderByDesc(Notification::getCreatedAt).orderByDesc(Notification::getId);
        return baseMapper.selectPage(new Page<>(effectiveNum, effectiveSize), wrapper);
    }

    @Override
    public long unreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId).isNull(Notification::getReadAt);
        return count(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        Notification notification = getOwned(userId, id);
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getId, notification.getId())
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now());
        update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now());
        update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOne(Long userId, Long id) {
        Notification notification = getOwned(userId, id);
        removeById(notification.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearRead(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId).isNotNull(Notification::getReadAt);
        remove(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Notification create(NotificationMessage message) {
        Notification notification = new Notification();
        notification.setUserId(message.getUserId());
        notification.setEventType(message.getEventType());
        notification.setLevel(message.getLevel() == null ? AlertConstants.SEVERITY_WARNING : message.getLevel());
        notification.setTitle(truncate(message.getTitle(), 200));
        notification.setContent(message.getContent());
        notification.setRefType(message.getRefType());
        notification.setRefId(message.getRefId());
        save(notification);
        trimReadBeyondRetention(message.getUserId());
        return notification;
    }

    private Notification getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "站内信不存在");
        }
        Notification notification = getById(id);
        if (notification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "站内信不存在");
        }
        if (!userId.equals(notification.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return notification;
    }

    /**
     * 已读条目保留上限：仅清理最旧的已读条目，未读条目一律保留（未读即待办，不得静默丢弃）。
     */
    private void trimReadBeyondRetention(Long userId) {
        int retention = properties.getInappRetentionPerUser();
        if (retention <= 0) {
            return;
        }
        LambdaQueryWrapper<Notification> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(Notification::getUserId, userId).isNotNull(Notification::getReadAt);
        long readCount = count(readWrapper);
        if (readCount <= retention) {
            return;
        }
        int overflow = (int) Math.min(readCount - retention, MAX_TRIM_PER_CALL);
        LambdaQueryWrapper<Notification> oldest = new LambdaQueryWrapper<>();
        oldest.eq(Notification::getUserId, userId).isNotNull(Notification::getReadAt)
                .orderByAsc(Notification::getReadAt).orderByAsc(Notification::getId);
        Page<Notification> page = new Page<>(1, overflow, false);
        List<Notification> expired = baseMapper.selectPage(page, oldest).getRecords();
        if (!expired.isEmpty()) {
            removeByIds(expired.stream().map(Notification::getId).toList());
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}