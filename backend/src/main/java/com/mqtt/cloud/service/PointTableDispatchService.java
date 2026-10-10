package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.response.PointDispatchResultDTO;

/**
 * 点表下发服务（T-31 P3，路线 A）。
 * <p>
 * 职责边界很窄：<b>把某设备启用中的 EDGE 点表原样推给边缘网关</b>。
 * 它不负责采集（P4）、不负责边缘侧如何应用载荷、也不负责下发历史查询
 * （下发历史复用命令记录，以 {@code source=POINT_TABLE} 过滤）。
 * <p>
 * 为什么不自己 publish：自行 publish 意味着要自建离线队列、重试退避、超时判定与回执幂等，
 * 这四件事在 {@link DeviceCommandService} 里都已有成熟实现且经过 T-15/T-16 验证。
 * 重复实现的代价不是代码量，而是<b>幂等语义极易在重写中丢失</b> —— 而重复应用一份点表
 * 恰恰是路线 A 最需要避免的事（会造成边缘侧采集周期被反复重置）。
 */
public interface PointTableDispatchService {

    /**
     * 下发点表到边缘网关。
     * <p>
     * 前置校验：设备存在且启用 → 接入协议支持边缘驱动 → 点表执行方为 {@code EDGE} →
     * 至少有一个启用点位。任何一条不满足都<b>整单拒绝</b>并给出明确错误码，
     * 不做「下发空点表」（空点表在边缘侧语义是「清空所有点位」，误发的破坏性远大于不下发）。
     *
     * @param ownerId  操作人（用于设备归属校验，平台侧任务可为 {@code null} 表示跳过）
     * @param deviceId 设备ID
     * @return 下发结果（命令ID、状态、版本号、点数）
     */
    PointDispatchResultDTO dispatch(Long ownerId, Long deviceId);
}
