package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备命令记录（T-15）。
 * <p>
 * 一次下行命令一行，状态流转 {@code PENDING → SENT → ACKED/FAILED/TIMEOUT}；
 * {@code command_id} 即下行 Alink 载荷的 {@code id}，设备回执按它关联。
 * <p>
 * T-16 扩展：新增 {@code QUEUED} 状态（设备离线 / property_set 发布失败时入队），
 * 并增加 {@code attemptCount} / {@code nextAttemptAt} 支持补发与指数退避。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_command_record")
public class DeviceCommandRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 命令唯一标识（UUID），回执关联键 */
    @TableField("command_id")
    private String commandId;

    @TableField("device_id")
    private Long deviceId;

    /** 下发时的产品ID（冗余，便于按产品统计） */
    @TableField("product_id")
    private Long productId;

    /** property_set / service */
    @TableField("command_type")
    private String commandType;

    /** 服务标识符；property_set 为 NULL */
    @TableField("identifier")
    private String identifier;

    /** 请求参数原文（JSON 文本） */
    @TableField("params")
    private String params;

    /** PENDING / SENT / QUEUED / ACKED / FAILED / TIMEOUT */
    @TableField("status")
    private String status;

    /** sync / async */
    @TableField("call_type")
    private String callType;

    /** CONSOLE / OPEN_API */
    @TableField("source")
    private String source;

    /** 控制台操作者用户ID；开放 API 为 NULL */
    @TableField("operator_id")
    private Long operatorId;

    /** 回执 data（JSON 文本） */
    @TableField("result")
    private String result;

    /** 失败原因（发布失败 / 回执非 200 / 超时） */
    @TableField("error_message")
    private String errorMessage;

    /** 创建时间（毫秒精度） */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 发布到 Broker 的时间 */
    @TableField("sent_at")
    private LocalDateTime sentAt;

    /** 终态时间（ACKED/FAILED/TIMEOUT） */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 补发尝试次数（QUEUED 阶段累计，T-16） */
    @TableField("attempt_count")
    private Integer attemptCount;

    /** 下次可补发时间（指数退避，T-16）；非 QUEUED 时为 NULL */
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;
}