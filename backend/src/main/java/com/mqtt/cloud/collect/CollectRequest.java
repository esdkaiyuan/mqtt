package com.mqtt.cloud.collect;

import com.mqtt.cloud.entity.DevicePointTable;

import java.time.Duration;
import java.util.List;

/**
 * 采集请求（T-31）
 * <p>
 * 一次「对单台设备的若干点位执行一轮采集」的输入。刻意以设备而非点位为粒度组织，
 * 因为协议栈的连接建立代价高，采集必须以「连一次、读多点」为单位才经济——
 * 单点位一次调用的实现会让现场总线往返次数随点位规模线性上升。
 *
 * @param points        待采集点位（已由调用方按执行方过滤，{@code enabled=1}）
 * @param driverConfig  驱动配置 JSON（{@code product.driver_config}），含连接参数等；可为空
 * @param connectTimeout 连接超时；实现必须遵守，不得无限等待
 * @param readTimeout    读超时；实现必须遵守
 */
public record CollectRequest(List<DevicePointTable> points,
                            String driverConfig,
                            Duration connectTimeout,
                            Duration readTimeout) {

    public CollectRequest {
        // 防御性拷贝：点位集合来自 Mapper 查询，采集可能在线程池中异步执行，
        // 直接持有可变列表会让并发修改泄漏到采集逻辑里
        points = points == null ? List.of() : List.copyOf(points);
    }
}
