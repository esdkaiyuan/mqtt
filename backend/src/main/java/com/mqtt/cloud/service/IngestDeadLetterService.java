package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.entity.IngestDeadLetter;
import com.mqtt.cloud.ingest.IngestRecord;

import java.util.List;
import java.util.Map;

/**
 * 摄取死信兜底：队列溢出与批量落库最终失败的消息在此留痕，避免静默丢弃。
 * <p>
 * 写入必须「自身异常不外抛」——否则会把摄取 worker 拖入无限重试；
 * 且写入在独立线程与独立事务中完成，回调线程与 worker 线程都不被死信写入阻塞。
 * <p>
 * <b>T-30 补齐处置闭环</b>：此前本接口只有写入与查询，表里虽定义了
 * {@code PENDING/REPLAYED/DISCARDED} 三态却无任何写路径，消息丢失后只能手工改库。
 * 现补 {@link #replay} / {@link #discard} / {@link #replayBatch}。
 */
public interface IngestDeadLetterService {

    /** 单条消息转入死信（队列溢出路径）。 */
    void record(IngestRecord record, String reason, String errorMessage);

    /** 整批消息转入死信（落库失败 / 停机排空超时路径）。 */
    void recordBatch(List<IngestRecord> records, String reason, String errorMessage);

    /** 管理员分页查询，status 为空时不过滤。 */
    IPage<IngestDeadLetter> page(String status, int pageNum, int pageSize);

    /**
     * 重放单条死信：把原始载荷重新投回摄取管线，受理成功后置 {@code REPLAYED}。
     * <p>
     * <b>语义</b>：返回 {@code true} 表示<b>已受理</b>，不代表已落库——
     * 与 HTTP 上报同构（fire-and-forget），避免同步等待 worker 把设备侧超时传导进来。
     * <p>
     * 幂等：已 {@code REPLAYED} / {@code DISCARDED} 的记录重复重放直接返回 {@code false}，
     * 防止同一条消息被反复重放造成数据翻倍。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 记录不存在或已被处置
     */
    boolean replay(Long id);

    /**
     * 丢弃单条死信：置 {@code DISCARDED}，不再重放。幂等，已处置返回 {@code false}。
     */
    boolean discard(Long id);

    /**
     * 批量重放指定 ID 的死信，返回实际提交重放的条数。
     * <p>
     * 逐条隔离：单条失败不阻断其余条目。
     */
    int replayBatch(List<Long> ids);

    /** 统计各状态死信条数，供健康检查与告警使用。 */
    Map<String, Long> countByStatus();
}