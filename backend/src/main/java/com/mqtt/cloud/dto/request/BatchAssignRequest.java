package com.mqtt.cloud.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 批量关联分组 / 标签请求（T-18 设计文档 §10.3）。
 * <p>
 * {@code action=ADD} 建立关联（唯一键去重，幂等）；{@code action=REMOVE} 解除关联。
 * {@code groupId} 与 {@code tagId} 二选一，由调用的端点决定使用哪个。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchAssignRequest extends BatchTargetRequest {

    /** 目标分组 ID（分组关联端点使用）。 */
    private Long groupId;

    /** 目标标签 ID（标签关联端点使用）。 */
    private Long tagId;

    /** ADD / REMOVE，缺省 ADD。 */
    private String action;
}