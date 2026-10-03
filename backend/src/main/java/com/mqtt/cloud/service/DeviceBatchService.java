package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.BatchAssignRequest;
import com.mqtt.cloud.dto.request.BatchCommandRequest;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.response.BatchOperationResult;
import com.mqtt.cloud.entity.Device;

import java.util.List;

/**
 * 设备批量操作服务（T-18 设计文档 §8）。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 * <p>
 * 目标集合由「手选设备 ID ∪ 产品设备 ∪ 分组（含子分组）设备 ∪ 标签设备」去重得到，并二次按当前用户过滤；
 * 逐台操作相互隔离，单台失败不阻断其余，结果以 {@link BatchOperationResult} 逐台返回。
 */
public interface DeviceBatchService {

    /** 单次批量上限：防止一次批量打爆 MQTT 发布与数据库写入。 */
    int MAX_BATCH_SIZE = 500;

    /**
     * 批量下发命令：目标集合逐台 {@code invoke}，强制异步；单台失败只记该台 {@code error}。
     */
    BatchOperationResult sendCommands(Long userId, BatchCommandRequest request);

    /** 批量启用 / 禁用：逐台复用 {@link DeviceService#enableDevice(Long)} / {@link DeviceService#disableDevice(Long)}，幂等。 */
    BatchOperationResult setEnabled(Long userId, BatchTargetRequest target, boolean enabled);

    /** 批量加入 / 移出分组（{@code add=true} 加入，唯一键去重幂等）。 */
    BatchOperationResult assignGroup(Long userId, BatchAssignRequest request, boolean add);

    /** 批量打 / 去标签（{@code add=true} 打标，唯一键去重幂等）。 */
    BatchOperationResult assignTag(Long userId, BatchAssignRequest request, boolean add);

    /**
     * 解析目标集合：手选 ∪ 产品（该产品下当前用户的设备）∪ 分组（含子分组）∪ 标签，
     * 去重后按当前用户过滤，按设备 ID 升序稳定输出。
     * <p>
     * {@code productIds} 为空即跳过（T-22 追加，既有调用方行为不变）。
     * 空集或超 {@link #MAX_BATCH_SIZE} 抛 {@code 6217}；分组 / 标签归属非法抛 {@code 6212} / {@code 6214}。
     */
    List<Device> resolveTarget(Long userId, BatchTargetRequest target);
}