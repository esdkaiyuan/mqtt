package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.entity.Device;

/**
 * 设备拓扑服务（T-26 设计文档 §7 / §8.2）。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 * <p>
 * 本期为两层拓扑：网关 → 子设备，子设备不得再有下级。挂载前的逐项校验与错误码对齐设计 §7。
 */
public interface DeviceTopologyService {

    /**
     * 把既有设备挂到网关上。
     * <p>
     * 校验通过后子设备角色置 {@code SUB} 并指向网关；网关当前为 {@code DIRECT} 时一并提升为
     * {@code GATEWAY}（首次挂载自动收敛角色）；两侧认证缓存随变更失效。
     *
     * @return 更新后的子设备
     */
    Device attach(Long gatewayId, Long subDeviceId);

    /**
     * 解绑：子设备 {@code parent_id} 置空、角色回落 {@code DIRECT}。
     * <p>
     * 网关角色保持不变 —— 角色代表「具备网关能力」，不因当下无子设备而回退。
     */
    void detach(Long gatewayId, Long subDeviceId);

    /** 列某网关的子设备（分页），过滤项沿用设备查询条件。 */
    IPage<Device> listSubDevices(Long gatewayId, DeviceQueryDTO query);

    /** 取子设备的父网关；该设备不存在或没有父时返回 {@code null}。 */
    Device findParent(Long subDeviceId);

    /**
     * 变更拓扑角色，取值仅 {@code DIRECT} 与 {@code GATEWAY}。
     * <p>
     * 网关下仍有子设备时不得降级（{@code 6259}）；重复设置同一角色为幂等空操作。
     *
     * @return 更新后的设备
     */
    Device changeNodeType(Long deviceId, String nodeType);
}
