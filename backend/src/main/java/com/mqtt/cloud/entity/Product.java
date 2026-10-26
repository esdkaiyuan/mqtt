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

    /**
     * 接入协议（T-30）：MQTT / HTTP / MODBUS / OPC_UA / GATEWAY_MQTT。
     * <p>
     * NULL 视为 {@code MQTT}，存量产品行为不变。这是平台唯一的「设备怎么连上来」抽象维度，
     * 路线 A（边缘驱动）与路线 B（平台侧主站）共用此字段。
     */
    @TableField("protocol_type")
    private String protocolType;

    /** 平台侧驱动标识（T-30路线 B：Modbus 主站等）；MQTT/HTTP 产品恒为 NULL */
    @TableField("driver_id")
    private String driverId;

    /** 驱动配置 JSON：点表默认值、轮询周期、连接参数等 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @TableField("driver_config")
    private String driverConfig;

    @TableField("topic_prefix")
    private String topicPrefix;

    @TableField("payload_format")
    private String payloadFormat;

    /**
     * Payload Codec（T-55）：把非 Alink 原始报文映射成 Alink JSON 的声明式模板。
     * <p>
     * NULL 表示按原始 Alink 解析，存量产品行为不变。配置为 JSON：
     * <pre>
     * {"version":1,"fallbackToRaw":true,"template":{
     *   "method":"thing.event.property.post",
     *   "params":{"temperature":"$.sensors.temp"}
     * }}
     * </pre>
     */
    @TableField(value = "payload_codec", updateStrategy = FieldStrategy.ALWAYS)
    private String payloadCodec;

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