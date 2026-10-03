package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO;
import com.mqtt.cloud.dto.response.PropertySeriesVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 属性时序服务（T-21 设计文档 §4.4 / §7.3）。
 * <p>
 * 两条契约：
 * <ul>
 *   <li>{@link #append(Long, List)}：由 {@code ThingModelInterpretServiceImpl} 的**第 4 条旁路**在属性解析成功
 *       后调用，复用同一份归一化样本批量追加落库。写入为纯追加（不做时间戳守卫、不去重），
 *       时间与失败隔离由调用方的 {@code try/catch} 兜底——本方法自身只负责「短路 + 组行 + 批量插入」。</li>
 *   <li>{@link #query(Long, PropertyHistoryQueryDTO)}：只读链路**设备鉴权 → 标识符物模型校验 → 参数归一化
 *       → 按 {@code numeric} 分组分桶聚合 → 组装序列**，返回逐「设备 × 属性」的桶序列。</li>
 * </ul>
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface PropertyHistoryService {

    /**
     * 追加一批属性历史（旁路写入）。
     * <p>
     * {@code app.property-history.enabled=false} 时直接短路返回（用于压测 / 极端降级），无异常。
     *
     * @param deviceId 设备ID
     * @param samples  本批成功归一化的属性样本（可能与告警 / 规则样本同源）
     */
    void append(Long deviceId, List<Sample> samples);

    /**
     * 分桶聚合查询：指定设备集合与属性标识符集合，在给定时间窗与桶粒度下返回聚合序列。
     * <p>
     * 约束：设备归属逐一校验（不存在 → {@code 2002}、非本人 → {@code 2003}）、标识符逐一物模型校验
     * （未建模 → {@code 6226}）、时间格式 / 跨度（→ {@code 6225}）、桶粒度白名单（→ {@code 6227}）、
     * 序列数上限（→ {@code 6225}）、桶数上限（→ {@code 6227}）。
     *
     * @param userId 当前登录用户 ID（设备归属校验用）
     * @param query  查询参数（设备 / 属性 / 时间窗 / 桶）
     * @return 按 {@code deviceId ASC, identifier ASC} 的序列列表，点按时间升序；无数据的组合 {@code points} 为空列表
     */
    List<PropertySeriesVO> query(Long userId, PropertyHistoryQueryDTO query);

    /** 属性历史样本：来自解析链路的标识符、数据类型、归一化文本值与上报时间。 */
    record Sample(String identifier, String dataType, String valueText, LocalDateTime reportedAt) {
    }
}
