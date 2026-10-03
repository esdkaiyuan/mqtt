package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.DeviceLogQueryDTO;
import com.mqtt.cloud.dto.response.DeviceLogItem;

/**
 * 设备统一日志服务（T-20 设计文档 §7.1 / §7.3）。
 * <p>
 * 只读能力：把四张存量表（{@code message} / {@code device_command_record} /
 * {@code device_event_record} / {@code device_status_history}）在<strong>查询时</strong>
 * 归并为单设备时间线。不新增表、不改写入路径。
 * <p>
 * <b>ArchUnit 门禁</b>：本接口所在层不得依赖 {@code ..mapper..}，仅暴露查询契约。
 */
public interface DeviceLogService {

    /**
     * 分页查询指定设备的统一日志时间线。
     * <p>
     * 设备鉴权、参数归一化（类型白名单 / 分页夹取 / 时间窗解析与跨度校验）、
     * 关联键回填（命令下发 → 回执）均在实现类内完成。
     *
     * @param userId   当前登录用户 ID（设备归属校验用）
     * @param deviceId 设备 ID（路径参数）
     * @param query    过滤与分页参数（可空字段表示不过滤）
     * @return 按 {@code occurred_at DESC, seq DESC} 倒序的分页结果；无数据时返回空页
     */
    IPage<DeviceLogItem> page(Long userId, Long deviceId, DeviceLogQueryDTO query);
}
