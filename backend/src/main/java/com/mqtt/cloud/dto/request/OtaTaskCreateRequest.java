package com.mqtt.cloud.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * OTA 升级任务创建请求（T-22 设计文档 §6.2）。
 * <p>
 * 结构校验（非空 / 长度）走 Bean Validation；语义校验（固件归属、目标解析结果非空且不超限、
 * 目标设备与固件产品一致）由服务层集中处理，统一抛 {@code 6231} / {@code 6237}。
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
}
