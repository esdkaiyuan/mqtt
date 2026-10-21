package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 上位机保存请求（T-38）。
 * <p>
 * {@code config} 为原始 JSON 字符串（结构见 {@code HmiConfig}），由服务层解析并逐控件校验，
 * 避免绑定层与校验层耦合。
 */
@Data
public class HmiSaveDTO {

    @NotBlank(message = "上位机名称不能为空")
    private String name;

    @NotNull(message = "绑定设备不能为空")
    private Long deviceId;

    @NotBlank(message = "上位机配置不能为空")
    private String config;
}
