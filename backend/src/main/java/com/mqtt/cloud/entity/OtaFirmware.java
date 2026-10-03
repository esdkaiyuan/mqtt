package com.mqtt.cloud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * OTA 固件包（T-22）。
 * <p>
 * 按 {@code (product_id, version)} 唯一；文件本体落盘到 {@code app.ota.storage-dir}，
 * 本表只存元数据与存储相对路径 {@code {productId}/{version}/{fileName}}。
 * 表无逻辑删除列，删除固件包时同步删除磁盘文件（被升级任务引用时禁止删除）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ota_firmware")
public class OtaFirmware {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 上传者（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    /** 所属产品（product.id） */
    @TableField("product_id")
    private Long productId;

    /** 固件版本号（产品内唯一） */
    @TableField("version")
    private String version;

    /** 原始文件名 */
    @TableField("file_name")
    private String fileName;

    /** 存储相对路径（{productId}/{version}/{fileName}） */
    @TableField("file_path")
    private String filePath;

    /** 文件字节数 */
    @TableField("file_size")
    private Long fileSize;

    /** 文件 MD5（小写十六进制），供设备校验 */
    @TableField("md5")
    private String md5;

    /** 版本说明 */
    @TableField("description")
    private String description;

    /** 创建时间，由表 DEFAULT 维护 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间，由表 ON UPDATE 维护 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
