package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 告警记录查询 DTO（T-17 设计文档 §10.1）。
 * <p>
 * 供 {@code GET /alerts} 与 {@code GET /external/v1/alerts} 绑定查询参数；
 * 所有过滤项均可空，空则不过滤；列表按 {@code last_triggered_at} 倒序。
 */
@Data
public class AlertQuery {

    /** 状态过滤：TRIGGERED / ACKNOWLEDGED / RECOVERED，选填 */
    private String status;

    /** 来源过滤：THRESHOLD / OFFLINE / EVENT，选填 */
    private String sourceType;

    /** 级别过滤：INFO / WARNING / CRITICAL，选填 */
    private String severity;

    /** 设备ID过滤，选填 */
    private Long deviceId;

    /** 仅看未恢复（status &lt;&gt; RECOVERED），选填；与 {@link #status} 同传时以 {@code status} 为准 */
    private Boolean openOnly;

    /** 页码，默认1 */
    private Integer pageNum = 1;

    /** 每页数量，默认10，最大100 */
    private Integer pageSize = 10;
}
