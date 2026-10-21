package com.mqtt.cloud.service;

/**
 * 平台侧 Modbus 采集调度（T-31 P4）。
 * <p>
 * 只做「找到期点位 → 按设备提交专用线程池」，真正的协议读取在 {@code PointCollector} 实现里。
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}。
 */
public interface ModbusCollectService {

    /**
     * 查一批到期的 PLATFORM 点位并按设备提交采集任务。
     *
     * @return 本轮提交的设备任务数；0 表示没有到期点位
     */
    int sweepDuePoints();
}
