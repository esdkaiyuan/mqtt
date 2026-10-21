package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.Device;

/**
 * 设备日志实时通知（T-41）。
 * <p>
 * 设备日志是查询时对消息 / 命令 / 事件 / 状态四路的 union，没有单一写入点。
 * 本接口不直接构造日志条目，只发一个「该设备日志有更新」的轻量事件，
 * 由前端按事件刷新时间线。这样避免在四条写路径上重复维护一份日志投影，
 * 也避免前端实时追加与分页查询出现两份不一致的数据。
 */
public interface DeviceLogNotifier {

    /** 通知订阅者：该设备产生了新的日志。实现必须吞掉异常，不得影响主链路。 */
    void notify(Device device);
}
