package com.mqtt.cloud.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * OTA 升级任务创建请求（T-22 设计文档 §6.2，T-32 增灰度参数）。
 * <p>
 * 结构校验（非空 / 长度 / 数值范围）走 Bean Validation；语义校验（灰度参数与策略的一致性、
 * 固件归属、目标解析结果非空且不超限、目标设备与固件产品一致、互斥过滤）由服务层集中处理，
 * 统一抛 {@code 6231} / {@code 6237} / {@code 6293} / {@code 6294}。
 */
@Data
public class OtaTaskCreateRequest {

    /** 任务名称，非空，长度 ≤ 64。 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 64, message = "任务名称长度不能超过 64")
    private String name;

    /** 固件包 ID，非空；须存在且归属当前用户。 */
    @NotNull(message = "固件包不能为空")
    private Long firmwareId;

    /** 升级目标集合（复用 T-18 目标 DTO，含设备 / 产品 / 分组 / 标签四维）；解析后非空。 */
    @NotNull(message = "升级目标不能为空")
    @Valid
    private BatchTargetRequest target;

    /**
     * 下发策略（T-32）：{@code ALL} 一次性全量 / {@code BATCHED} 灰度分批，缺省 {@code ALL}。
     * <p>
     * 取值大小写不敏感；非法值一律拒绝而非静默归到 {@code ALL} ——
     * 静默降级会让运维以为自己在做灰度，实际全量下发，正是灰度要防的事故。
     */
    @Size(max = 16, message = "下发策略长度不能超过 16")
    private String batchMode;

    /**
     * 每批台数（T-32），仅 {@code BATCHED} 生效，取值 {@code [1, 100000]}，
     * 缺省时由服务端取 {@code app.ota.default-batch-size}。
     */
    @Min(value = 1, message = "每批台数不能小于 1")
    @Max(value = 100000, message = "每批台数超出允许范围")
    private Integer batchSize;

    /**
     * 批次推进间隔毫秒（T-32），仅 {@code BATCHED} 生效；{@code 0} 或缺省 = 仅手动推进。
     * <p>
     * 不设上限校验：间隔过大不会造成资源问题，只会让任务推得慢，由运维自己填。
     */
    private Long batchIntervalMs;
}
