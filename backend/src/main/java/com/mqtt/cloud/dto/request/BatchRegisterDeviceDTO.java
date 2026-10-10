package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 批量注册设备请求（T-30）。
 * <p>
 * 平台按 {@code namePattern} + 序号生成 deviceKey，无需逐台提交，
 * 这正是与阿里云/腾讯云「批量注册实例」对齐的关键点。
 */
@Data
public class BatchRegisterDeviceDTO {

    @NotNull(message = "产品ID不能为空")
    private Long productId;

    /** deviceKey 前缀，如 {@code sensor}；生成结果为 {@code prefix-index} */
    @NotBlank(message = "设备Key前缀不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{1,48}$",
             message = "前缀仅允许字母、数字、中划线、下划线，长度 1~48")
    private String prefix;

    /** 设备类型，落 device.device_type */
    @NotBlank(message = "设备类型不能为空")
    @Size(max = 50, message = "设备类型长度不能超过50个字符")
    private String deviceType;

    /** 设备数量，上限 1000 */
    @NotNull(message = "注册数量不能为空")
    @Min(value = 1, message = "注册数量至少为1")
    @Max(value = 1000, message = "注册数量最多为1000")
    private Integer count;

    /** 起始序号，默认 1 */
    @Min(value = 0, message = "起始序号不能为负数")
    private Integer startIndex;

    /** 设备名称模板，默认取 prefix */
    @Size(max = 128, message = "名称模板长度不能超过128个字符")
    private String namePattern;

    @Size(max = 255, message = "MQTT Topic长度不能超过255个字符")
    private String topic;

    @Size(max = 500, message = "设备描述长度不能超过500个字符")
    private String description;

    /** 是否为每台设备签发激活码，默认 true */
    private Boolean issueActivateCode;
}
