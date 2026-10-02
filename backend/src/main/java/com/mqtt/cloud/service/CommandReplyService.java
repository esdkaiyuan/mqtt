package com.mqtt.cloud.service;

import com.mqtt.cloud.ingest.ResolvedEvent;

import java.util.List;

/**
 * 命令回执处理（T-15 设计文档 §9）。
 * <p>
 * 由 {@code IngestDispatcher} 在落库事务提交后作为旁路调用，只处理 {@code messageType=reply} 的消息：
 * 按回执 {@code id} 关联命令记录，{@code code=200} 置 {@code ACKED}、否则置 {@code FAILED}。
 * 实现必须逐条隔离异常且绝不冒泡（冒泡会导致 worker 整批重试、消息重复落库）。
 */
public interface CommandReplyService {

    void handle(List<ResolvedEvent> events);
}