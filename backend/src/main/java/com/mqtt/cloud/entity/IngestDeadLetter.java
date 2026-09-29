package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上行摄取死信
 * 对应数据库表：ingest_dead_letter
 */
@Data
@TableName("ingest_dead_letter")
public class IngestDeadLetter {

    /** 处置状态：待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** 处置状态：已重放 */
    public static final String STATUS_REPLAYED = "REPLAYED";
    /** 处置状态：已丢弃 */
    public static final String STATUS_DISCARDED = "DISCARDED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_key")
    private String deviceKey;

    @TableField("topic")
    private String topic;

    @TableField("message_type")
    private String messageType;

    @TableField("payload")
    private String payload;

    @TableField("qos")
    private Integer qos;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    /** 进入死信的原因：queue_full / persist_failed */
    @TableField("reason")
    private String reason;

    /** 落库尝试次数 */
    @TableField("attempts")
    private Integer attempts;

    @TableField("error_message")
    private String errorMessage;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}