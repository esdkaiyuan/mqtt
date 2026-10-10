package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备注册批次实体（T-30）。
 * <p>
 * 一次批量创建落一条批次记录，承载成功 / 失败计数与一次性凭据明文。
 * 凭据明文仅在创建时返回一次，平台不提供二次查询（与单设备创建一致）。
 */
@Data
@TableName("device_reg_batch")
public class DeviceRegBatch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 批次号（对外唯一标识） */
    @TableField("batch_no")
    private String batchNo;

    @TableField("product_id")
    private Long productId;

    @TableField("owner_id")
    private Long ownerId;

    /** deviceKey 生成模板，如 {@code sensor-{index}} */
    @TableField("name_pattern")
    private String namePattern;

    @TableField("prefix")
    private String prefix;

    @TableField("start_index")
    private Integer startIndex;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("succeeded_count")
    private Integer succeededCount;

    @TableField("failed_count")
    private Integer failedCount;

    /** 失败明细 JSON 数组：{@code [{index, deviceKey, reason}]} */
    @TableField("failed_items")
    private String failedItems;

    /** 一次性凭据明文 JSON 数组：{@code [{deviceKey, username, secret}]} */
    @TableField("credentials")
    private String credentials;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
