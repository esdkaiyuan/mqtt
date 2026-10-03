package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OTA 固件升级配置（T-22 设计文档 §9.2）。
 * <p>
 * 形状对齐 {@link PropertyHistoryProperties}：{@code @Data + @Component + @ConfigurationProperties}，
 * 全部字段带默认值，未配置时按默认值生效。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ota")
public class OtaProperties {

    /** 总开关：关闭后固件上传 / 建任务直接拒绝（{@code 6232}）。 */
    private boolean enabled = true;

    /** 固件落盘根目录（backend 容器内路径，须与 nginx 共享卷一致）。 */
    private String storageDir = "/var/lib/mqtt/ota";

    /** 设备侧下载地址前缀（nginx 静态托管基址，不含末尾斜杠）。 */
    private String publicBaseUrl = "http://localhost/firmware";

    /** 单个固件文件大小上限（字节），默认 32 MiB。 */
    private long maxFileSize = 33554432L;

    /** 单任务目标设备数上限。 */
    private int taskMaxDevices = 500;

    /** 补投 / 超时巡检间隔（毫秒），默认 60s。 */
    private long dispatchIntervalMs = 60000L;

    /** 单轮补投的 `PENDING` 记录上限。 */
    private int dispatchBatchSize = 200;

    /** 逐台记录超时阈值（毫秒），默认 24h；超过即置 `TIMEOUT`。 */
    private long taskTimeoutMs = 86400000L;
}
