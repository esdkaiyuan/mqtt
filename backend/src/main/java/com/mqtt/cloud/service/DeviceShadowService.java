package com.mqtt.cloud.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 设备影子读写（T-16 设计文档 §8）。
 * <p>
 * 影子以 {@code desired} / {@code reported} / {@code delta} 三份状态描述设备，
 * 内部统一走「读-改-写 + 版本 CAS」，并在 CAS 冲突时重试；重试耗尽仅记 WARN 放弃本次合并，
 * 不阻断命令与上报主链路（影子是最终一致的尽力视图）。
 * <p>
 * 本接口不得依赖 {@code mapper} 包（ArchUnit 规则 4）。
 */
public interface DeviceShadowService {

    /**
     * 查询影子（懒创建：行不存在时先建空行再读）。
     *
     * @param deviceId  设备 ID
     * @param productId 产品 ID，仅用于判定 {@code modeled}
     * @return 影子视图；影子为空时返回空映射与 {@code version = 0}
     */
    DeviceShadowResponse get(Long deviceId, Long productId);

    /**
     * 批量只读查询：一次取回多台设备的影子，不创建缺失行。
     * <p>
     * {@code modeled} 仍按产品判定，但同一产品只会查询一次物模型；返回映射保证包含
     * 每个入参设备（缺失影子返回空视图与 version=0）。
     */
    Map<Long, DeviceShadowResponse> getBatch(List<DeviceShadowKey> devices);

    /**
     * 上报收敛：把给定标识符的<b>权威最新值</b>（回读 {@code device_property_latest}）合并进
     * {@code reported} 并重算 {@code delta}。
     * <p>
     * 不回写调用方传入的值：乱序旧包已在 {@code upsertIfNewer} 的时间戳守卫处被丢弃，
     * 回读得到的即为权威值，影子天然继承同一份守卫。
     *
     * @param identifiers 本次消息中成功归一化的属性标识符（可为空）
     */
    void applyReported(Long deviceId, Collection<String> identifiers);

    /**
     * 期望值写入：把归一化后的期望值合并进 {@code desired} 并重算 {@code delta}。
     *
     * @param desired 已归一化的 {@code identifier -> 文本} 映射（可为空）
     */
    void applyDesired(Long deviceId, Map<String, String> desired);

    /**
     * 期望值写入（带来源）：行为同 {@link #applyDesired(Long, Map)}，并把来源写入变更事件，
     * 供场景影子触发过滤内部自动化（T-45）。
     *
     * @param source 命令来源（{@code CONSOLE} / {@code OPEN_API} / {@code SCENE} 等），可空
     */
    void applyDesired(Long deviceId, Map<String, String> desired, String source);

    /**
     * 影子视图（对外响应体，见设计文档 §8.1）。
     */
    record DeviceShadowResponse(
            boolean modeled,
            long version,
            Map<String, String> desired,
            Map<String, String> reported,
            Map<String, String> delta,
            LocalDateTime updatedAt) {
    }

    record DeviceShadowKey(Long deviceId, Long productId) {
    }
}
