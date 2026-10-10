package com.mqtt.cloud.collect;

import java.time.LocalDateTime;

/**
 * 单点采集结果（T-31）
 * <p>
 * 一个点位一轮采集的结果，<b>成功与失败用同一个类型表达</b>而非异常。
 * 原因见 {@link PointCollector#collect}：失败是现场采集的常态（设备离线、地址配错、
 * 寄存器越界），把它们建模为返回值而非异常，才能让「一台设备 20 个点位里失败 3 个」
 * 这种真实场景被完整记录，而不会因抛出异常丢掉另外 17 个点的结果。
 *
 * @param pointId      点位ID
 * @param deviceId     设备ID（冗余，便于批量落 {@code device_point_run} 时免于回查）
 * @param pointKey     点位标识（映射到物模型 identifier）
 * @param success      本点位是否采集成功
 * @param rawValue     原始值（未经换算）；失败时为 {@code null}
 * @param value        换算后工程值（{@code 原始值 * scale + offset}）；失败时为 {@code null}
 * @param errorMessage 失败原因；成功时为 {@code null}
 * @param collectedAt  采集时刻
 */
public record PointValue(Long pointId,
                         Long deviceId,
                         String pointKey,
                         boolean success,
                         String rawValue,
                         String value,
                         String errorMessage,
                         LocalDateTime collectedAt) {

    /** 构造成功结果；{@code collectedAt} 传 {@code null} 表示由落库层补当前时间。 */
    public static PointValue ok(Long pointId, Long deviceId, String pointKey,
                                String rawValue, String value) {
        return new PointValue(pointId, deviceId, pointKey, true, rawValue, value, null, null);
    }

    /** 构造失败结果。失败原因必填——空原因的失败日志在排障时等于没有日志。 */
    public static PointValue failed(Long pointId, Long deviceId, String pointKey, String errorMessage) {
        return new PointValue(pointId, deviceId, pointKey, false, null, null,
                errorMessage == null || errorMessage.isBlank() ? "未知采集失败" : errorMessage, null);
    }
}
