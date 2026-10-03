package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.OtaTaskCreateRequest;
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.dto.response.OtaTaskVO;

import java.util.List;

/**
 * OTA 升级任务服务（T-22 设计文档 §7.1 / §7.3）。
 * <p>
 * 负责任务创建（目标解析 + 逐台记录落库 + 即时下发）、列表 / 详情 / 逐台记录分页、
 * 重投与删除，以及供巡检调用的补投与超时扫描。计数与任务状态统一由
 * {@code OtaUpgradeTaskMapper#refreshCounters} 聚合重算，不在业务路径上逐条增减。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code ..mapper..}，持久化细节只出现在
 * {@code service.impl}。
 */
public interface OtaUpgradeService {

    /**
     * 创建升级任务：解析目标集合、剔除产品不匹配设备、落库任务与逐台记录、即时下发。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 固件不存在或不属于该用户抛 {@code 6231}；
     *         目标为空 / 超限 / 与固件产品不匹配抛 {@code 6237}
     */
    OtaTaskDetailVO createTask(Long userId, OtaTaskCreateRequest request);

    /** 任务列表分页（按创建时间倒序）。 */
    IPage<OtaTaskVO> listTasks(Long userId, long page, long size);

    /** 任务详情（含目标快照）；不存在或不属于该用户抛 {@code 6236}。 */
    OtaTaskDetailVO detail(Long userId, Long id);

    /** 任务下逐台记录分页；{@code status} 为空时不过滤。 */
    IPage<OtaRecordVO> listRecords(Long userId, Long id, String status, long page, long size);

    /**
     * 重投：将任务下 {@code PENDING/FAILED/TIMEOUT} 记录重置为 {@code PENDING} 并重新下发。
     *
     * @return 重置并按批尝试下发的记录数
     * @throws com.mqtt.cloud.common.exception.BusinessException 任务不存在抛 {@code 6236}；
     *         任务状态为 {@code SUCCESS} 抛 {@code 6238}
     */
    int retry(Long userId, Long id);

    /** 删除任务（逐台记录 {@code ON DELETE CASCADE} 级联清理）；{@code RUNNING} 任务抛 {@code 6238}。 */
    void remove(Long userId, Long id);

    /**
     * 对指定逐台记录批量下发固件地址（供建任务即时下发与巡检补投共用）。
     * <p>
     * 单台失败只记该条 {@code message} 并保持 {@code PENDING}，不阻断其余设备。
     *
     * @param taskId    任务主键
     * @param recordIds 待下发的记录主键
     * @return 成功下发的记录数
     */
    int dispatchTask(Long taskId, List<Long> recordIds);

    /** 巡检：取 {@code PENDING} 且设备在线的记录，按 {@code limit} 分批补投。 */
    int sweepPending(int limit);

    /** 巡检：将超时未回传的记录置 {@code TIMEOUT}，并刷新受影响任务计数。 */
    int sweepTimeout(long timeoutMs);
}
