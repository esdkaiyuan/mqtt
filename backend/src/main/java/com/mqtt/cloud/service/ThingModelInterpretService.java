package com.mqtt.cloud.service;

import com.mqtt.cloud.ingest.ResolvedEvent;

import java.util.List;

/**
 * 上行数据解析：把 Alink JSON 子集按产品物模型翻译为属性最新值与事件记录（T-14 §7）。
 * <p>
 * 由 {@code IngestDispatcher} 在落库事务提交后调用；实现须<b>逐条隔离</b>，
 * 单条异常只记指标与 WARN，不抛出、不中断本批其余消息、不进入死信。
 */
public interface ThingModelInterpretService {

    /** 解析一批已落库的上行事件；未建模 / 非 Alink / 未知标识符均静默跳过并计数。 */
    void interpret(List<ResolvedEvent> events);
}