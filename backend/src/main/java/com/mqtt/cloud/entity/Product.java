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

    /**
     * 物模型 TSL JSON（属性/事件/服务），见 T-14 设计文档。
     * <p>
     * 不随产品响应序列化：物模型走独立端点 {@code /products/{id}/thing-model}，
     * 避免列表接口携带整份 TSL，也避免经产品更新接口形成第二条写路径。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @TableField("thing_model")
    private String thingModel;

    /** 物模型版本号，每次保存 +1；0 表示未建模 */
    @TableField("thing_model_version")
    private Integer thingModelVersion;

    /** 物模型最后保存时间（同样只在物模型端点暴露） */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @TableField("thing_model_updated_at")
    private LocalDateTime thingModelUpdatedAt;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}