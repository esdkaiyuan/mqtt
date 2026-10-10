package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.entity.Notification;
import com.mqtt.cloud.service.notification.NotificationMessage;

/**
 * 站内信服务（T-27 设计文档 §6.1 / §10.1）。
 * <p>
 * 站内信按 {@code user_id} 隔离，仅可读写本人数据；写入由 {@code InAppChannel} 调用，
 * 读取与已读由控制台端点调用。本接口位于 {@code service} 包，按 ArchUnit 分层规则
 * **不得依赖 {@code mapper} 包**，持久化细节只在 {@code service.impl} 中处理。
 */
public interface NotificationService extends IService<Notification> {

    /** 分页查询本人站内信，按 {@code created_at} 倒序；{@code unreadOnly} 为 true 时仅未读。 */
    IPage<Notification> page(Long userId, Boolean unreadOnly, Integer pageNum, Integer pageSize);

    /** 未读数：本人 {@code read_at IS NULL} 的条数。 */
    long unreadCount(Long userId);

    /** 标记单条已读；不存在抛 {@code 404}，非本人抛 {@code 403}。 */
    void markRead(Long userId, Long id);

    /** 全部标记已读。 */
    void markAllRead(Long userId);

    /** 删除单条；不存在抛 {@code 404}，非本人抛 {@code 403}。 */
    void deleteOne(Long userId, Long id);

    /** 清空本人已读站内信。 */
    void clearRead(Long userId);

    /**
     * 落一条站内信（内置必达通道的唯一写入口）。
     * <p>
     * 写入后按 {@code app.notification.inapp-retention-per-user} 清理最旧的已读条目（配置 {@code <= 0} 不清理）。
     */
    Notification create(NotificationMessage message);
}