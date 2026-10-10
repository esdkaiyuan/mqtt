package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.NotificationChannelConfigRequest;
import com.mqtt.cloud.entity.NotificationChannelConfig;

import java.util.List;

/**
 * 通知渠道配置服务（T-27 设计文档 §6.2 / §7 / §10.1）。
 * <p>
 * 渠道配置按 {@code user_id} 隔离，读写前先做归属校验；响应中的敏感字段（SMTP 密码、
 * 短信密钥、自定义请求头）一律脱敏为 {@code ******}，更新时留空或回填脱敏串表示保留原值。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**，
 * 持久化细节只在 {@code service.impl} 中处理。
 */
public interface NotificationChannelConfigService extends IService<NotificationChannelConfig> {

    /** 列出本人渠道配置（敏感字段已脱敏）。 */
    List<NotificationChannelConfig> listByUser(Long userId);

    /** 取本人渠道配置（敏感字段已脱敏）；不存在抛 {@code 404}，非本人抛 {@code 403}。 */
    NotificationChannelConfig getOwned(Long userId, Long id);

    /** 新建渠道配置：校验矩阵见设计 §7，命中即抛对应业务码；返回脱敏后的配置。 */
    NotificationChannelConfig create(Long userId, NotificationChannelConfigRequest request);

    /** 更新渠道配置：类型不可变更；敏感字段留空或回填脱敏串时保留原值。 */
    NotificationChannelConfig update(Long userId, Long id, NotificationChannelConfigRequest request);

    /** 删除渠道配置；不存在抛 {@code 404}，非本人抛 {@code 403}。 */
    void delete(Long userId, Long id);

    /**
     * 取本人启用中且订阅指定事件的渠道行（未脱敏，供投递使用）。
     * <p>
     * 事件匹配口径与 {@code webhook_config.events} 一致：{@code events} 数组包含事件名即命中。
     */
    List<NotificationChannelConfig> listActiveMatching(Long userId, String channelType, String eventType);

    /** 按 ID 取渠道行（未脱敏，供投递与重试使用）；不存在返回 {@code null}。 */
    NotificationChannelConfig getRawById(Long id);

    /** 投递成功：刷新 {@code last_triggered_at} 并清零连续失败计数。 */
    void markSuccess(Long id);

    /** 投递失败：连续失败计数 +1。 */
    void markFailure(Long id);
}