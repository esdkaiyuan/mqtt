package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一日志条目（T-20 设计文档 §5）。
 * <p>
 * <b>只读查询投影，非表实体</b>：由 {@code DeviceLogMapper} 的 {@code UNION ALL} 聚合四张存量表
 * （{@code message} / {@code device_command_record} / {@code device_event_record} / {@code device_status_history}）
 * 投影而来，各来源缺失的列置 {@code null}。字段分三组：
 * <ul>
 *   <li>公共列：{@link #logId} / {@link #logType} / {@link #direction} / {@link #occurredAt} / {@link #title}
 *       / {@link #subType} / {@link #identifier} / {@link #status} / {@link #payload}；</li>
 *   <li>来源专属可空列：{@link #topic} / {@link #qos} / {@link #receivedAt}（MESSAGE）、
 *       {@link #commandId} / {@link #callType} / {@link #source} / {@link #operatorId} / {@link #result}
 *       / {@link #errorMessage} / {@link #attemptCount} / {@link #finishedAt}（COMMAND）、
 *       {@link #eventType}（EVENT）；</li>
 *   <li>关联键：{@link #correlationId} / {@link #correlationRole}（服务层 Java 侧回填）。</li>
 * </ul>
 * {@link #title} 与关联键由服务层生成，SQL 不投影；{@link #seq} 为来源主键，仅供排序稳定。
 */
@Data
public class DeviceLogItem {

    /** 条目唯一键：{@code {前缀}-{来源主键}}，如 {@code MSG-88012} / {@code CMD-1024}，前端列表 key。 */
    private String logId;

    /** 日志类型：MESSAGE / COMMAND / EVENT / STATUS。 */
    private String logType;

    /** 方向：MESSAGE 取报文方向（PUBLISH / SUBSCRIBE）、COMMAND=DOWN、EVENT=UP、STATUS=null。 */
    private String direction;

    /** 归并排序键：报文 sent_at / 命令 created_at / 事件 reported_at / 连接 timestamp。 */
    private LocalDateTime occurredAt;

    /** 展示标题（服务层生成）：如「命令：switch」「上行报文：reply」「设备上线」。 */
    private String title;

    /** 子类型：MESSAGE=topic、COMMAND=command_type、EVENT=event_type、STATUS=status。 */
    private String subType;

    /** 标识符：COMMAND / EVENT 的 {@code identifier}；MESSAGE / STATUS 为 null。 */
    private String identifier;

    /** 状态：COMMAND 的 {@code status}、STATUS 的 {@code ONLINE/OFFLINE}；MESSAGE / EVENT 为 null。 */
    private String status;

    /** 原始载荷：MESSAGE=payload、COMMAND=params、EVENT=output_data、STATUS=null。 */
    private String payload;

    // -------- MESSAGE 专属 --------

    /** 报文主题（仅 MESSAGE）。 */
    private String topic;

    /** QoS（仅 MESSAGE）。 */
    private Integer qos;

    /** 报文入库时间 {@code received_at}（仅 MESSAGE）。 */
    private LocalDateTime receivedAt;

    // -------- COMMAND 专属 --------

    /** 命令 ID {@code command_id}（仅 COMMAND），同时作为关联键。 */
    private String commandId;

    /** 调用方式 {@code call_type}（仅 COMMAND）。 */
    private String callType;

    /** 命令来源 {@code source}（仅 COMMAND）。 */
    private String source;

    /** 操作人 ID {@code operator_id}（仅 COMMAND）。 */
    private Long operatorId;

    /** 命令执行结果 {@code result}（仅 COMMAND）。 */
    private String result;

    /** 失败原因 {@code error_message}（仅 COMMAND）。 */
    private String errorMessage;

    /** 重试次数 {@code attempt_count}（仅 COMMAND）。 */
    private Integer attemptCount;

    /** 命令完成时间 {@code finished_at}（仅 COMMAND）。 */
    private LocalDateTime finishedAt;

    // -------- EVENT 专属 --------

    /** 事件类型 {@code event_type}（info/alert/fault，仅 EVENT）。 */
    private String eventType;

    // -------- 关联（服务层回填） --------

    /** 关联键：COMMAND 取 command_id；MESSAGE 且 topic 以 /reply 结尾时取载荷 id；其余 null。 */
    private String correlationId;

    /** 关联角色：REQUEST（命令下发）/ REPLY（回执报文）/ null。 */
    private String correlationRole;

    /** 来源主键，仅供 {@code ORDER BY occurred_at DESC, seq DESC} 稳定排序。 */
    private Long seq;
}
