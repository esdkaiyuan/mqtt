package com.mqtt.cloud.service;

import com.mqtt.cloud.ingest.ResolvedEvent;

import java.util.List;

/**
 * OTA 升级进度回传处理（T-22 设计文档 §7.3）。
 * <p>
 * 由 {@code IngestDispatcher} 在落库事务提交后作为旁路调用，只处理 {@code messageType=ota} 的消息：
 * 解析设备上行 {@code device/{key}/ota} 报文，归位到该设备最近一条非终态升级记录并推进状态机
 * （进度单调不减、状态只允许前向迁移）。
 * <p>
 * 实现必须逐条隔离异常且绝不冒泡（冒泡会导致 worker 整批重试、消息重复落库）。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code ..mapper..}，持久化细节只出现在
 * {@code service.impl}。
 */
public interface OtaProgressService {

    /**
     * 处理一批已解析设备的上行事件；非 {@code ota} 类型、非法报文、无归属记录的条目被安全忽略。
     */
    void handle(List<ResolvedEvent> events);
}
