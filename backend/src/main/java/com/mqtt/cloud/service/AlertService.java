package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.entity.AlertRecord;

import java.util.List;

/**
 * 告警记录服务（T-17 设计文档 §10.1）。
 * <p>
 * 控制台列表 / 详情 / 确认 / 恢复 / 未读数；记录按 {@code user_id} 隔离，
 * 仅可读写本人数据。本接口位于 {@code service} 包，按 ArchUnit 分层规则
 * **不得依赖 {@code mapper} 包**，持久化细节只在 {@code service.impl} 中处理。
 */
public interface AlertService extends IService<AlertRecord> {

    /** 分页查询本人告警，按 {@code last_triggered_at} 倒序；过滤项空则不过滤。 */
    IPage<AlertRecord> page(Long userId, AlertQuery query);

    /** 取本人告警详情；不存在抛 {@code 6209}，非本人抛 {@code 403}。 */
    AlertRecord getOwned(Long userId, Long id);

    /** 人工确认：仅 {@code TRIGGERED} 可确认，否则抛 {@code 6210}。 */
    void acknowledge(Long userId, Long id);

    /** 人工恢复：仅活动告警可恢复，否则抛 {@code 6210}；成功后发 {@code alert.recovered} 通知。 */
    void recover(Long userId, Long id);

    /** 未读数：本人全部活动告警数（{@code status <> 'RECOVERED'}）。 */
    long unreadCount(Long userId);

    /** 顶栏最近告警：本人活动告警按 {@code last_triggered_at} 倒序取前 N 条。 */
    List<AlertRecord> recent(Long userId, int limit);
}