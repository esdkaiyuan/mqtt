package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 告警指派请求（T-27 设计文档 §5.3）。
 * <p>
 * {@code assigneeId} 为空表示解除指派；{@code notify} 为真时投递一次 {@code alert.escalated}
 * 通知（默认不通知，避免与自动升级重复轰炸）。
 */
@Data
public class AlertAssignRequest {

    /** 处理人用户ID；为空表示解除指派 */
    private Long assigneeId;

    /** 是否投递一次升级通知，默认不通知 */
    private Boolean notify;
}