package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * OTA 逐台升级记录 Mapper（T-22 设计文档 §7.1）。
 * <p>
 * 单表 CRUD 继承 {@code BaseMapper}；补投巡检（{@code OtaDispatchSweeper}）与进度回传旁路
 * （{@code OtaProgressService}）各用一个自定义查询：
 * <ul>
 *   <li>{@link #selectDispatchCandidates(String, int)}：取仍在 {@code PENDING} 且设备在线的记录，
 *       用于批量补投；</li>
 *   <li>{@link #selectLatestActiveByDevice(Long)}：取某设备最近一条进行中的记录，用于把上行进度
 *       归位到正确的任务。</li>
 * </ul>
 * 注意：{@code ota_upgrade_record} 无逻辑删除列，随任务删除时须物理删除。
 */
@Mapper
public interface OtaUpgradeRecordMapper extends BaseMapper<OtaUpgradeRecord> {

    /**
     * 取指定状态且设备在线的升级记录，按主键升序，最多 {@code limit} 条。
     * <p>
     * 仅用于补投（{@code status = 'PENDING'}）：设备离线时下发必然失败，故在此过滤，避免无谓下发。
     *
     * @param status 记录状态（补投场景固定传 {@code PENDING}）
     * @param limit  单批上限，配合 {@code app.ota.dispatch-batch-size}
     * @return 候选记录（无匹配返回空列表）
     */
    List<OtaUpgradeRecord> selectDispatchCandidates(@Param("status") String status, @Param("limit") int limit);

    /**
     * 取某设备最近一条进行中（{@code PENDING/DISPATCHED/DOWNLOADING/FLASHING}）的升级记录。
     * <p>
     * 设备上行 {@code device/{key}/ota} 不带任务标识，故以「最近一条未终态记录」为其归属；
     * 命中 {@code idx_device}，按主键降序取第一条。
     *
     * @param deviceId 设备主键
     * @return 最近一条进行中记录；无则返回 {@code null}
     */
    OtaUpgradeRecord selectLatestActiveByDevice(@Param("deviceId") Long deviceId);

    /**
     * 取给定设备集合中「已在别的任务里仍非终态」的 device_id（T-32 / G-11-4 设备级任务互斥）。
     * <p>
     * 只认 {@code excludeTaskId} 之外、且所属任务处于 {@code RUNNING} / {@code PAUSED} 的记录：
     * 已 {@code ABORTED} 的任务其记录由 abort 一次性置 {@code SKIPPED}（终态），本就不会出现在结果里；
     * 已 {@code SUCCESS} / {@code PARTIAL} / {@code FAILED} 的任务已收口，不构成并发升级风险。
     * <p>
     * 命中 {@code idx_device}；调用方须保证 {@code deviceIds} 非空（本项目所有设备 ID 均来自
     * 库内查询，天然非空），否则 {@code IN ()} 会是语法错误。
     *
     * @param deviceIds     待检查的设备集合（非空）
     * @param excludeTaskId 排除的任务主键（传当前任务自身，使本任务的既有记录不算冲突）
     * @return 冲突的 device_id 集合（升序，无冲突返回空列表）
     */
    List<Long> selectActiveDeviceIdsExcludingTask(@Param("deviceIds") Collection<Long> deviceIds,
                                                  @Param("excludeTaskId") Long excludeTaskId);

    /**
     * 取某任务全部仍为 {@code PENDING} 的记录主键（T-32 终止时批量置 {@code SKIPPED}）。
     *
     * @param taskId 任务主键
     * @return 记录主键升序列表（无则空列表）
     */
    List<Long> selectPendingIdsByTask(@Param("taskId") Long taskId);
}
