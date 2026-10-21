package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.OtaTaskCreateRequest;
import com.mqtt.cloud.dto.response.OtaBatchVO;
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.dto.response.OtaTaskVO;

import java.util.List;

/**
 * OTA 升级任务服务（T-22 设计文档 §7.1 / §7.3，T-32 增灰度分批与设备互斥）。
 * <p>
 * 负责任务创建（目标解析 + 互斥过滤 + 分批落库 + 下发首批）、列表 / 详情 / 逐台记录 / 批次查询、
 * 重投、暂停 / 恢复 / 终止 / 手动推进，以及供巡检调用的补投、灰度推进与超时扫描。
 * 计数与状态统一由 {@code OtaUpgradeTaskMapper#refreshCounters} /
 * {@code OtaUpgradeBatchMapper#refreshBatchCounters} 聚合重算，不在业务路径上逐条增减。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code ..mapper..}，持久化细节只出现在
 * {@code service.impl}。
 */
public interface OtaUpgradeService {

    /**
     * 创建升级任务：解析目标集合 → 剔除产品不匹配设备 → 按互斥策略剔除/拒绝冲突设备 →
     * 按灰度策略切批落库 → 仅下发首批（{@code ALL} 策略即全部）。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 固件不存在或不属于该用户抛 {@code 6231}；
     *         目标为空 / 超限 / 与固件产品不匹配抛 {@code 6237}；
     *         目标设备全部被其他升级任务占用抛 {@code 6293}；
     *         灰度参数非法抛 {@code 6294}
     */
    OtaTaskDetailVO createTask(Long userId, OtaTaskCreateRequest request);

    /** 任务列表分页（按创建时间倒序）。 */
    IPage<OtaTaskVO> listTasks(Long userId, long page, long size);

    /** 任务详情（含目标快照）；不存在或不属于该用户抛 {@code 6236}。 */
    OtaTaskDetailVO detail(Long userId, Long id);

    /** 任务下逐台记录分页；{@code status} 为空时不过滤。 */
    IPage<OtaRecordVO> listRecords(Long userId, Long id, String status, long page, long size);

    /**
     * 任务批次列表（T-32 灰度视图），按批号升序。
     * <p>
     * {@code ALL} 策略的任务恒只有一个批次，与改造前行为等价；存量任务由 V19 回填的批次行为空时
     * 返回空列表，前端据此隐藏灰度面板。
     */
    List<OtaBatchVO> listBatches(Long userId, Long id);

    /**
     * 重投：将任务下 {@code PENDING/FAILED/TIMEOUT} 记录重置为 {@code PENDING} 并重新下发。
     * <p>
     * 终态任务的重投不会改变灰度批次划分：重投只作用于记录本身，不新建批次。
     *
     * @return 重置并按批尝试下发的记录数
     * @throws com.mqtt.cloud.common.exception.BusinessException 任务不存在抛 {@code 6236}；
     *         任务状态为 {@code SUCCESS} 或 {@code ABORTED} 抛 {@code 6238}
     */
    int retry(Long userId, Long id);

    /**
     * 暂停灰度下发（T-32 / G-11-1）：任务置 {@code PAUSED}，不再开新批。
     * <p>
     * 在途批次与已下发设备**不受影响**，继续跑到终态；仅阻断后续批次推进。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 任务不存在抛 {@code 6236}；
     *         非 {@code RUNNING} 状态抛 {@code 6238}
     */
    OtaTaskDetailVO pause(Long userId, Long id);

    /**
     * 恢复灰度下发（T-32 / G-11-1）：把 {@code PAUSED} 任务放回 {@code RUNNING}，
     * 之后既可自动推进（{@code batch_interval_ms > 0}）也可手动推进。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 任务不存在抛 {@code 6236}；
     *         非 {@code PAUSED} 状态抛 {@code 6238}
     */
    OtaTaskDetailVO resume(Long userId, Long id);

    /**
     * 终止灰度下发（T-32 / G-11-1）：任务置 {@code ABORTED}，未下发的记录批量置 {@code SKIPPED}
     * （**不计入失败**），未下发的批次置 {@code SKIPPED}。
     * <p>
     * 已下发的在途记录不强制中断，仍按其自身节奏走到 {@code SUCCESS/FAILED/TIMEOUT}；
     * 但任务状态被 {@code ABORTED} 持有，不会被聚合改写。
     *
     * @return 被跳过的记录数
     * @throws com.mqtt.cloud.common.exception.BusinessException 任务不存在抛 {@code 6236}；
     *         非 {@code RUNNING} / {@code PAUSED} 状态抛 {@code 6238}
     */
    int abort(Long userId, Long id);

    /**
     * 手动推进一个批次（T-32 / G-11-1）：立即下发「下一批」。
     * <p>
     * 与自动推进的区别只是不受 {@code batch_interval_ms} 约束——人工确认上一批成功率后
     * 立刻铺下一批，不必等间隔。
     *
     * @return 本批实际下发成功数（无待下发批次时返回 0）
     * @throws com.mqtt.cloud.common.exception.BusinessException 任务不存在抛 {@code 6236}；
     *         任务非 {@code RUNNING} 或已无非灰度批次抛 {@code 6238}
     */
    int advance(Long userId, Long id);

    /** 删除任务（逐台记录与批次 {@code ON DELETE CASCADE} 级联清理）；{@code RUNNING} 任务抛 {@code 6238}。 */
    void remove(Long userId, Long id);

    /**
     * 对指定逐台记录批量下发固件地址（供建任务即时下发、灰度推进与巡检补投共用）。
     * <p>
     * 单台失败只记该条 {@code message} 并保持 {@code PENDING}，不阻断其余设备。
     * 下发前会做一次批量互斥二次校验，命中「设备已在别的升级任务中」的记录本轮跳过并写明原因，
     * 待对方任务收口后由补投巡检自然重投。
     *
     * @param taskId    任务主键
     * @param recordIds 待下发的记录主键
     * @return 成功下发的记录数
     */
    int dispatchTask(Long taskId, List<Long> recordIds);

    /** 巡检：取 {@code PENDING} 且设备在线的记录，按 {@code limit} 分批补投。 */
    int sweepPending(int limit);

    /**
     * 巡检：推进到期的灰度批次（T-32）。
     * <p>
     * 一个批次到期需同时满足：任务 {@code RUNNING}、策略 {@code BATCHED}、间隔 {@code > 0}、
     * 本批是当前批号的下一批、且上一批已无非终态记录。
     *
     * @param batchIntervalMs 批次推进间隔（毫秒）
     * @param limit           单轮推进批次数上限
     * @return 本轮实际下发成功的设备数
     */
    int sweepBatchAdvance(long batchIntervalMs, int limit);

    /** 巡检：将超时未回传的记录置 {@code TIMEOUT}，并刷新受影响任务与批次计数。 */
    int sweepTimeout(long timeoutMs);
}