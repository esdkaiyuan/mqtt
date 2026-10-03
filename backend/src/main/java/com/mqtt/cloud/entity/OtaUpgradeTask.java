package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * OTA 升级任务（T-22）。
 * <p>
 * {@code target_json} 记录创建时的目标快照（{@code deviceIds/productIds/groupIds/tagIds}），
 * 以 {@code String} 承载原始 JSON，读写时由服务层用 {@code ObjectMapper} 解析，避免实体耦合 Jackson 注解
 * （与 {@link Dashboard} 同款做法）。
 * <p>
 * 计数（{@code total/dispatched/success/failed}）与 {@code status} 由 {@code refreshCounters}
 * 聚合 {@code ota_upgrade_record} 重算，不在读路径上推算。
 * {@code firmware_id} 外键不级联：固件被任务引用时禁止删除，由服务层前置拦截。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ota_upgrade_task")
public class OtaUpgradeTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建者（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    /** 固件包（ota_firmware.id），不级联删除 */
    @TableField("firmware_id")
    private Long firmwareId;

    /** 固件所属产品（冗余，便于筛选） */
    @TableField("product_id")
    private Long productId;

    /** 任务名称 */
    @TableField("name")
    private String name;

    /** 目标快照（deviceIds/productIds/groupIds/tagIds，JSON 文本） */
    @TableField("target_json")
    private String targetJson;

    /** 目标设备总数 */
    @TableField("total_count")
    private Integer totalCount;

    /** 已成功下发数 */
    @TableField("dispatched_count")
    private Integer dispatchedCount;

    /** 升级成功数 */
    @TableField("success_count")
    private Integer successCount;

    /** 升级失败/超时数 */
    @TableField("failed_count")
    private Integer failedCount;

    /** 任务状态（RUNNING/SUCCESS/PARTIAL/FAILED） */
    @TableField("status")
    private String status;

    /** 创建时间，由表 DEFAULT 维护 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间，由表 ON UPDATE 维护 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
