package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 命令下发请求（T-15 设计文档 §10.1）。
 * <p>
 * {@code type=property_set} 时 {@code identifier} 必须为空；{@code type=service} 时必填。
 * {@code callType} 可选，缺省 {@code async}；{@code sync} 表示等待回执到终态再返回。
 */
@Data
public class CommandInvokeRequest {

    /** property_set / service */
    @NotBlank(message = "命令类型不能为空")
    private String type;

    /** 服务标识符；property_set 必须为空 */
    private String identifier;

    /** 请求参数（JSON 对象） */
    @NotNull(message = "params 不能为空")
    private JsonNode params;

    /** sync / async，缺省 async */
    private String callType;
}