package com.mqtt.cloud.service;

import com.mqtt.cloud.ingest.ResolvedEvent;

import java.util.List;

/**
 * 设备上线补发分发（T-16 设计文档 §9.2）。
 * <p>
 * 作为摄取落库后的第五路 fan-out：设备上报 {@code data} / {@code heartbeat} 即视为上线，
 * 据此触发该设备 {@code QUEUED} 命令的补发。
 * <p>
 * 与 {@link ThingModelInterpretService} / {@link CommandReplyService} 同一约定：
 * <b>逐条隔离、绝不冒泡</b>——异常若冒泡会导致 worker 整批重试、消息重复落库。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 */
public interface ShadowDeliveryService {

    /**
     * 处理一批已落库的上行事件：按设备去重后，对存在 {@code QUEUED} 命令的设备触发补发。
     *
     * @param events 本批已解析出设备的事件（可为空）
     */
    void onIngest(List<ResolvedEvent> events);
}
