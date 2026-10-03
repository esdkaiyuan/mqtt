package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OTA 固件包视图（T-22 设计文档 §6.1）。
 * <p>
 * <b>只读投影，非表实体</b>。{@code downloadUrl} 由服务层按 {@code app.ota.public-base-url}
 * 与存储相对路径拼装，供前端展示与设备侧地址核对。
 */
@Data
public class OtaFirmwareVO {

    private Long id;

    private Long productId;

    /** 产品名称（服务层回填）。 */
    private String productName;

    private String version;

    private String fileName;

    /** 文件字节数。 */
    private Long fileSize;

    /** 文件 MD5（小写十六进制）。 */
    private String md5;

    /** 设备侧下载地址（nginx 静态托管）。 */
    private String downloadUrl;

    private String description;

    private LocalDateTime createdAt;
}
