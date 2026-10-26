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

    /**
     * 设备级互斥策略（T-32 / G-11-4）：目标设备已在别的升级任务中非终态时的处理方式。
     * <ul>
     *   <li>{@code SKIP}（默认）：静默剔除冲突设备继续建任务，并在创建响应中回传被剔除数 —— 批量场景下
     *       「跳过已在升级的设备」比整单失败更符合运维预期；</li>
     *   <li>{@code REJECT`：存在任一冲突设备即整单拒绝 —— 严格场景（如要求升级清单与预期完全一致）用。</li>
     * </ul>
     * 两种策略下，**下发前**仍会做一次批量二次校验，防止「A、B 两个任务并发创建」绕过创建期检查。
     */
    private String deviceConflictPolicy = "SKIP";

    /**
     * 灰度批次自动推进间隔（毫秒），默认 0 = 仅手动推进（T-32 / G-11-1）。
     * <p>
     * 大于 0 时由 {@code OtaBatchSweeper} 按此间隔自动开下一批；为 0 时批次只会因
     * 「上一批全部进入终态」而推进，或由控制台显式调用推进接口。
     * <p>
     * 不提供「按批次成功率自动熔断」：那是依赖现场数据的策略，误判代价（整批不下发）高于收益，
     * 留给后续按真实反馈再加。
     */
    private long batchIntervalMs = 0L;

    /** 单轮自动推进的批次数上限（T-32）。 */
    private int batchAdvanceBatchSize = 5;

    /** 灰度分批时的默认每批台数（T-32），创建请求未指定 {@code batchSize} 时取此值。 */
    private int defaultBatchSize = 10;

    /**
     * 固件完整性校验是否强制（T-32 / G-11-2），默认 false。
     * <p>
     * false（默认）：设备上报 {@code success} 但未回传 md5 时，判 {@code NOT_REPORTED} 并**放行成功**，
     * 保证存量未升级固件的设备不会因平台新增校验能力而全量转红；
     * true：未回传 md5 的 success 一律判失败，仅适用于设备侧已全量支持回传 md5 的现场。
     * <p>
     * 无论本开关如何，**设备回传了 md5 且与平台不一致时一律判失败**——那说明固件在传输中损坏。
     */
    private boolean verifyRequired = false;

    /**
     * 是否允许固件降级（T-54），默认 false。
     * <p>
     * false 时建任务会跳过「设备当前 {@code firmware_version} 高于目标版本」的设备，
     * 并在创建响应 / 任务详情中回传 {@code downgradeSkippedCount}；
     * true 时不做版本比较，按原行为全量下发（现场需要回滚固件时打开）。
     */
    private boolean allowDowngrade = false;

    /** 补投 / 超时巡检间隔（毫秒），默认 60s。 */
    private long dispatchIntervalMs = 60000L;

    /** 单轮补投的 `PENDING` 记录上限。 */
    private int dispatchBatchSize = 200;

    /** 逐台记录超时阈值（毫秒），默认 24h；超过即置 `TIMEOUT`。 */
    private long taskTimeoutMs = 86400000L;
}
