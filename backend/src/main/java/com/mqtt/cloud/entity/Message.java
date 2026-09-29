package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息实体
 * 对应数据库表：message
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("message")
public class Message {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** MQTT Topic */
    @TableField("topic")
    private String topic;

    /** 消息方向：PUBLISH/SUBSCRIBE */
    @TableField("direction")
    private String direction;

    /** 消息载荷内容（原始字符串） */
    @TableField("payload")
    private String payload;

    /** QoS等级：0/1/2 */
    @TableField("qos")
    private Integer qos;

    /** 关联设备ID */
    @TableField("device_id")
    private Long deviceId;

    /** 消息发送时间 */
    @TableField("sent_at")
    private LocalDateTime sentAt;

    /** 消息接收时间 */
    @TableField(value = "received_at", fill = FieldFill.INSERT)
    private LocalDateTime receivedAt;
}
