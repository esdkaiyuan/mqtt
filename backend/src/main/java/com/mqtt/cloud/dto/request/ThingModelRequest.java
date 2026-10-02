package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 物模型写入请求（保存 / 导入 / 校验共用）。
 * <p>
 * {@code thingModel} 为 TSL 原文（JSON 字符串），服务端统一校验后再落库。
 */
@Data
public class ThingModelRequest {

    @NotBlank(message = "物模型内容不能为空")
    private String thingModel;
}