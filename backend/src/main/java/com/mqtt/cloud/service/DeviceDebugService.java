package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.response.DebugCodeResponse;
import com.mqtt.cloud.dto.response.DebugContextResponse;
import com.mqtt.cloud.dto.response.DebugSimulateResponse;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import tools.jackson.databind.JsonNode;

/**
 * 设备调试中心（T-37）。
 * <p>
 * 只做三件事：把当前设备的接入信息投影成上下文、把模拟上行送进既有摄取管线、
 * 按物模型生成设备端 / 上位机端属性代码。归属校验由控制层完成，本接口不重复。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}。
 */
public interface DeviceDebugService {

    /** 当前设备的连接参数、Topic、物模型投影与属性最新值。 */
    DebugContextResponse context(Device device, Product product);

    /**
     * 模拟上行：与真实设备走同一条 {@code IngestPipeline}，触发物模型解析、影子、规则、告警、Webhook 与 SSE。
     *
     * @param operatorId  操作者，仅用于审计日志
     * @param messageType data / heartbeat / lwt / reply / ota
     */
    DebugSimulateResponse simulate(Long operatorId, Device device, Product product,
                                   String messageType, JsonNode payload);

    /**
     * 属性代码生成。
     *
     * @param target   {@code device}（设备端）或 {@code host}（上位机端）
     * @param language javascript / python / java / arduino（Arduino 仅设备端）
     */
    DebugCodeResponse code(Device device, Product product, String target, String language);
}
