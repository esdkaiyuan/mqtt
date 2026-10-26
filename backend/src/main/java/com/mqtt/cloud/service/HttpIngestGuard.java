package com.mqtt.cloud.service;

import java.time.LocalDateTime;

/**
 * HTTP 上报接入守卫（T-53）：设备级限流 + 幂等去重。
 * <p>
 * 限流解决「单台设备异常刷接口打满后端 / 数据库」；幂等解决「设备弱网重试导致同一条数据落库两次」。
 * 两者都在 {@link HttpIngestService#ingest} 的**参数校验之后、入队之前**执行：
 * 非法报文不占用配额，也不会写入幂等键（否则设备改好报文重试会被误判为重复）。
 */
public interface HttpIngestGuard {

    /**
     * 执行限流与幂等检查。
     *
     * @param deviceKey      设备键（已认证）
     * @param idempotencyKey 请求头 {@code Idempotency-Key}，可空；空表示不做幂等去重
     * @return 检查结果；{@code duplicate=true} 时调用方应直接返回、不再入队
     * @throws com.mqtt.cloud.common.exception.BusinessException 超出频率（{@code 6314}）
     *                                                             或幂等键非法（{@code 6315}）
     */
    GuardResult check(String deviceKey, String idempotencyKey);

    /**
     * 检查结果。
     *
     * @param duplicate       是否为重复请求（幂等键已存在）
     * @param firstAcceptedAt 首次受理时间；非重复时为当前时间
     */
    record GuardResult(boolean duplicate, LocalDateTime firstAcceptedAt) {
    }
}