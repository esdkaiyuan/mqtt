package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.entity.NotificationDelivery;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知投递记录服务（T-27 设计文档 §6.3 / §8.2）。
 * <p>
 * 记录一次「事件 × 渠道」的投递尝试，供审计与重试巡检使用；投递记录写入失败只记 WARN，
 * 不影响站内信落库与其他渠道投递（设计文档 §4.2）。本接口位于 {@code service} 包，
 * 按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface NotificationDeliveryService extends IService<NotificationDelivery> {

    /**
     * 落一条投递记录。
     * <p>
     * 成功置 {@code SUCCESS} 且不排重试；失败置 {@code FAILED}，未达 {@code max-attempts} 时
     * 按 {@code backoff-seconds} 写入 {@code next_attempt_at} 交由巡检重试，达上限则不再重试。
     *
     * @param message     分发载荷（提供 user_id / event_type / title 快照）
     * @param channelType 渠道类型
     * @param channelId   渠道配置 ID；内置通道传 {@code null}
     * @param result      渠道投递结果
     */
    NotificationDelivery record(NotificationMessage message, String channelType, Long channelId,
                                ChannelSendResult result);

    /**
     * 取到期需重投的记录：{@code status=FAILED} 且 {@code next_attempt_at <= now}，按到期时间升序。
     *
     * @param limit 单轮上限
     */
    List<NotificationDelivery> retryDue(int limit);

    /**
     * 清理创建时间早于 {@code cutoff} 的投递记录（保留策略），返回清理条数。
     */
    int purgeBefore(LocalDateTime cutoff);

    /**
     * 把一次重投结果写回记录（T-27 设计文档 §8.2）。
     * <p>
     * 成功置 {@code SUCCESS} 并清空 {@code next_attempt_at}；失败则 {@code attempt_count + 1}，
     * 未达 {@code max-attempts} 按指数退避顺延，达上限或无匹配目标（{@code skip}）置终态不再重试。
     */
    void applyRetryResult(NotificationDelivery delivery, ChannelSendResult result);
}