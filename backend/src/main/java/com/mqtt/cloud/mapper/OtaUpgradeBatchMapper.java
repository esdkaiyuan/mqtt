package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.OtaUpgradeBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OTA 升级批次 Mapper（T-32）。
 * <p>
 * 单表 CRUD 继承 {@code BaseMapper}；另有两个自定义查询：
 * <ul>
 *   <li>{@link #selectDueBatches(LocalDateTime, int)}：灰度自动推进的到期批次选取，命中
 *       {@code idx_status_scheduled}；</li>
 *   <li>{@link #refreshBatchCounters(Long)}：按同 {@code (task_id, batch_no)} 的记录重算批次计数与状态，
 *       与任务级 {@code refreshCounters} 同款「聚合而非增减」口径。</li>
 * </ul>
 * 注意：{@code ota_upgrade_batch} 无逻辑删除列，随任务删除时物理级联清理。
 */
@Mapper
public interface OtaUpgradeBatchMapper extends BaseMapper<OtaUpgradeBatch> {

    /**
     * 取已到期、可以推进的下一批次（T-32 灰度自动推进）。
     * <p>
     * 一个批次「到期」须同时满足：所属任务 {@code RUNNING}、策略为 {@code BATCHED}、
     * {@code batch_interval_ms > 0}（为 0 的任务只在控制台手动推进）、本批是当前批号的下一批、
     * 且**上一批已无任何非终态记录**。详见同文件 SQL 内注释。
     *
     * @param now             当前时间（间隔起算的判定基准）
     * @param batchIntervalMs 批次推进间隔（毫秒）
     * @param limit           单轮推进批次数上限
     * @return 可推进批次（按主键升序，无则空列表）
     */
    List<OtaUpgradeBatch> selectDueBatches(@Param("now") LocalDateTime now,
                                           @Param("batchIntervalMs") long batchIntervalMs,
                                           @Param("limit") int limit);

    /**
     * 按 {@code ota_upgrade_record} 重算批次计数与状态。
     * <p>
     * 以 {@code (taskId, batchNo)} 而非 {@code batchId} 定位：唯一键 {@code uk_task_batch} 保证命中单行，
     * 且 SQL 上完全规避「UPDATE 子查询引用目标表」的 MySQL 限制。
     * 口径与任务级一致：{@code dispatched_count} 排除 {@code SKIPPED}；批次本身已为 {@code SKIPPED} 时
     * 状态保持不变（终止不是失败）。
     *
     * @param taskId  任务主键
     * @param batchNo 批号
     * @return 受影响行数（0 表示该批次暂无记录）
     */
    int refreshBatchCounters(@Param("taskId") Long taskId, @Param("batchNo") Integer batchNo);
}