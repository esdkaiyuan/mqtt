package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 批量操作目标集合（T-18 设计文档 §7.3 / §8.1）。
 * <p>
 * 目标为「手选设备 ID ∪ 分组（含子分组）设备 ∪ 标签设备」去重后的并集；
 * 分组与标签在服务端校验归属并展开，最终设备集合再按当前用户过滤。
 */
@Data
public class BatchTargetRequest {

    /** 手选设备 ID 集合，可空。 */
    private List<Long> deviceIds;

    /**
     * 产品 ID 集合（该产品下全部设备），可空。
     * <p>
     * T-22 追加：用于 OTA 按产品批次升级。为空即跳过，既有 {@code deviceIds/groupIds/tagIds} 行为不变。
     */
    private List<Long> productIds;

    /** 分组 ID 集合（每个分组均含其所有子分组），可空。 */
    private List<Long> groupIds;

    /** 标签 ID 集合，可空。 */
    private List<Long> tagIds;
}