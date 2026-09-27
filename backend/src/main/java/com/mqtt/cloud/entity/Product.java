package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("product")
public class Product {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_key")
    private String productKey;

    @TableField("product_name")
    private String productName;

    @TableField("description")
    private String description;

    @TableField("auth_mode")
    private String authMode;

    @TableField("topic_prefix")
    private String topicPrefix;

    @TableField("payload_format")
    private String payloadFormat;

    @TableField("metadata_schema")
    private String metadataSchema;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}