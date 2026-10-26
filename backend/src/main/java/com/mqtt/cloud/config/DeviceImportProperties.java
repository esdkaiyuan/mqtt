package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备 CSV 导入配置（T-53）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.device-import")
public class DeviceImportProperties {

    /** 单次导入文件大小上限（字节） */
    private int maxFileBytes = 2 * 1024 * 1024;

    /** 单次导入最大数据行数（不含表头） */
    private int maxRows = 1000;
}