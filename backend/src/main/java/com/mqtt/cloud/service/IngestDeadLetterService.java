package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.entity.IngestDeadLetter;
import com.mqtt.cloud.ingest.IngestRecord;

import java.util.List;

/**
 * 摄取死信兜底：队列溢出与批量落库最终失败的消息在此留痕，避免静默丢弃。
 * <p>
 * 写入必须「自身异常不外抛」——否则会把摄取 worker 拖入无限重试；
 * 且写入在独立线程与独立事务中完成，回调线程与 worker 线程都不被死信写入阻塞。
 */
public interface IngestDeadLetterService {

    /** 单条消息转入死信（队列溢出路径）。 */
    void record(IngestRecord record, String reason, String errorMessage);

    /** 整批消息转入死信（落库失败 / 停机排空超时路径）。 */
    void recordBatch(List<IngestRecord> records, String reason, String errorMessage);

    /** 管理员分页查询，status 为空时不过滤。 */
    IPage<IngestDeadLetter> page(String status, int pageNum, int pageSize);
}