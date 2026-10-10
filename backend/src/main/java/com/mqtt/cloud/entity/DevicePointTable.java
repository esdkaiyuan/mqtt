package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备点位实体（T-31）
 * <p>
 * 对应数据库表：{@code device_point_table}（由 V18 迁移创建）。
 * <p>
 * 这是路线 A（边缘采集）与路线 B（平台侧主站采集）<b>共用的寄存器/节点映射模型</b>，
 * 两者唯一的差异是 {@link #executor} 这一列——谁来执行这次采集。数据模型统一，
 * 使「逐台灰度切换路线」不需要改产品定义、不需要重建点表、不需要迁移数据。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("device_point_table")
public class DevicePointTable {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属设备ID（采集网关或子设备） */
    @TableField("device_id")
    private Long deviceId;

    /** 点位标识（映射到物模型属性/事件 identifier） */
    @TableField("point_key")
    private String pointKey;

    /**
     * 点位类型：HOLDING_REGISTER / INPUT_REGISTER / COIL / DISCRETE_INPUT / TAG / NODE。
     * <p>取值口径见 {@code PointTypeValue}，此处不做归一化（原样存取，校验在服务层）。</p>
     */
    @TableField("point_type")
    private String pointType;

    /** 地址（寄存器号/节点ID/tag 名） */
    @TableField("address")
    private String address;

    /** 数据类型 int/float/double/bool/string */
    @TableField("data_type")
    private String dataType;

    /** 线性缩放系数（工程值 = 原始值 * scale + offset） */
    @TableField("scale")
    private BigDecimal scale;

    /** 线性偏移（工程值 = 原始值 * scale + offset） */
    @TableField("offset")
    private BigDecimal offset;

    /** 单位 */
    @TableField("unit")
    private String unit;

    /**
     * 执行方：{@code EDGE}（下发给边缘网关自行采集）/ {@code PLATFORM}（平台侧主站轮询采集）。
     * <p>建表默认 {@code EDGE}；同一台设备下必须唯一，取值见 {@code PointExecutorValue}。</p>
     */
    @TableField("executor")
    private String executor;

    /** 采集周期（毫秒）。运行期会由采集调度钳制到配置下限，防止打爆现场总线。 */
    @TableField("read_interval_ms")
    private Integer readIntervalMs;

    /** 是否启用（1 启用 / 0 停用） */
    @TableField("enabled")
    private Integer enabled;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
