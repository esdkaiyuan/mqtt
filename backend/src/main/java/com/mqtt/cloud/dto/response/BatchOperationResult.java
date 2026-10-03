package com.mqtt.cloud.dto.response;

import java.util.List;

/**
 * 批量操作结果（T-18 设计文档 §5.2）。
 * <p>
 * 逐台结果聚合：单台失败只记入该台的 {@code error}，不影响其余。
 * 命令类批量额外携带 {@code commandId} / {@code status}，其余场景为 {@code null}。
 */
public record BatchOperationResult(int total, int succeeded, int failed, List<Item> items) {

    /** 单台结果。 */
    public record Item(Long deviceId,
                       String deviceKey,
                       String deviceName,
                       boolean success,
                       String commandId,
                       String status,
                       String error) {

        public static Item ok(Long deviceId, String deviceKey, String deviceName,
                              String commandId, String status) {
            return new Item(deviceId, deviceKey, deviceName, true, commandId, status, null);
        }

        public static Item fail(Long deviceId, String deviceKey, String deviceName, String error) {
            return new Item(deviceId, deviceKey, deviceName, false, null, null, error);
        }
    }

    public static BatchOperationResult of(List<Item> items) {
        int succeeded = (int) items.stream().filter(Item::success).count();
        return new BatchOperationResult(items.size(), succeeded, items.size() - succeeded, items);
    }
}