package com.mqtt.cloud.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

/**
 * 批量下发命令请求（T-18 设计文档 §10.3）。
 * <p>
 * 参数校验逐台进行（每台设备的物模型可能不同），单台失败只影响该台。
 * {@code callType} 被忽略并强制为 {@code async}：同步需逐台阻塞等待回执，批量场景不可接受。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchCommandRequest extends BatchTargetRequest {

    /** property_set / service */
    private String type;

    /** 服务标识符；property_set 必须为空 */
    private String identifier;

    /** 请求参数（JSON 对象） */
    private JsonNode params;
}