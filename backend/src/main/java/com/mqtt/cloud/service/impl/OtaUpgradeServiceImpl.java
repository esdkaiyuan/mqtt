package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.OtaStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.OtaProperties;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.request.OtaTaskCreateRequest;
import com.mqtt.cloud.dto.response.OtaBatchVO;
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.dto.response.OtaTaskVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.entity.OtaUpgradeBatch;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import com.mqtt.cloud.entity.OtaUpgradeTask;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.OtaFirmwareMapper;
import com.mqtt.cloud.mapper.OtaUpgradeBatchMapper;
import com.mqtt.cloud.mapper.OtaUpgradeRecordMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.OtaUpgradeService;
import com.mqtt.cloud.service.ProductService;
import com.mqtt.cloud.util.FirmwareVersionComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OTA 升级任务服务实现（T-22 设计文档 §7.3，T-32 增灰度分批与设备互斥）。
 * <p>
 * 目标集合解析复用 {@link DeviceBatchService#resolveTarget}（含产品维度），避免二份解析逻辑分叉；
 * 固件地址下发复用 {@link DeviceCommandService#invoke}（{@code service / ota_upgrade / async / OTA}），
 * 从而免费获得命令记录、回执更新、超时巡检与前端命令历史。
 * <p>
 * T-22 关键语义（T-32 保持不变）：
 * <ul>
 *   <li>单台下发失败只写该条 {@code message} 并**保持 {@code PENDING}**，等待补投巡检，不阻断整批；</li>
 *   <li>任务计数与状态不在读路径推算，统一由 {@code refreshCounters} 聚合 {@code ota_upgrade_record} 重算；</li>
 *   <li>{@code ota_upgrade_task} / {@code ota_upgrade_record} / {@code ota_upgrade_batch} 均无逻辑删除列，删除走物理删除。</li>
 * </ul>
 * <p>
 * T-32 新增语义：
 * <ul>
 *   <li><b>灰度（G-11-1）</b>：{@code BATCHED} 策略把目标切成若干批次，建任务时只下发首批；
 *       后续批次由 {@link #sweepBatchAdvance} 按间隔自动推进，或由控制台 {@link #advance} 手动推进。
 *       「上一批必须已无非终态记录」是推进前提 —— 前一批成功率未验证就继续铺量等于放弃灰度。</li>
 *   <li><b>暂停 / 终止</b>：{@code PAUSED} 与 {@code ABORTED} 是由操作产生的状态，
 *       {@code refreshCounters} 会保留它们不被聚合覆盖（见 SQL 首个 CASE 分支）；
 *       终止时未下发的记录置 {@code SKIPPED} 且**不计入失败**。</li>
 *   <li><b>设备互斥（G-11-4）</b>：建任务时按策略剔除 / 拒绝「已在别的升级任务中」的设备；
 *       下发前再做一次批量二次校验，防止并发建任务绕过创建期检查。
 *       二次校验命中冲突的记录保持 {@code PENDING} 并写明原因，待对方任务收口后由补投巡检自然重投。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaUpgradeServiceImpl implements OtaUpgradeService {

    /** 单页上限：防止 pageSize 被放大成全表扫描。 */
    static final long MAX_PAGE_SIZE = 100L;

    /** 单条记录 {@code message} 长度上限（与下行错误文案截断保持一致）。 */
    static final int ERROR_MESSAGE_MAX = 255;

    /** 超时兜底写回的说明文案。 */
    static final String TIMEOUT_MESSAGE = "升级超时（长时间无进度回传）";

    /**
     * 建任务阶段做互斥查询时的「排除任务」占位值。
     * <p>
     * 此时新任务尚未落库、没有 id 可传，而 SQL 里是 {@code task_id <> #{excludeTaskId}}：
     * 传 {@code null} 会让该条件恒为 false（SQL 三值逻辑），把所有设备都判成冲突，必须给非空占位。
     */
    private static final long NO_TASK_ID = 0L;

    private final OtaFirmwareMapper firmwareMapper;
    private final OtaUpgradeTaskMapper taskMapper;
    private final OtaUpgradeRecordMapper recordMapper;
    private final OtaUpgradeBatchMapper batchMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceBatchService deviceBatchService;
    private final DeviceCommandService deviceCommandService;
    private final ProductService productService;
    private final OtaProperties properties;
    private final ObjectMapper objectMapper;

    // ---------- 任务创建与查询 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OtaTaskDetailVO createTask(Long userId, OtaTaskCreateRequest request) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_INVALID, "OTA 功能已关闭");
        }
        if (request == null) {
            throw new BusinessException(ResultCode.OTA_TASK_INVALID, "升级任务参数不能为空");
        }
        OtaFirmware firmware = requireOwnedFirmware(userId, request.getFirmwareId());
        Canary canary = resolveCanary(request);

        // 目标集合：手选 ∪ 产品 ∪ 分组（含子分组）∪ 标签，已按当前用户二次过滤
        List<Device> targets = deviceBatchService.resolveTarget(userId, request.getTarget());
        List<Device> matched = targets.stream()
                .filter(device -> Objects.equals(firmware.getProductId(), device.getProductId()))
                .toList();
        if (matched.isEmpty()) {
            throw new BusinessException(ResultCode.OTA_TASK_INVALID, "目标设备中没有与该固件产品一致的设备");
        }

        // G-11-4 设备级互斥：剔除已在其他升级任务中的设备
        List<Device> versionEligible = filterVersionDowngrades(matched, firmware.getVersion());
        int downgradeSkipped = matched.size() - versionEligible.size();
        List<Device> available = excludeConflictingDevices(versionEligible);

        int maxDevices = properties.getTaskMaxDevices();
        if (maxDevices > 0 && available.size() > maxDevices) {
            throw new BusinessException(ResultCode.OTA_TASK_INVALID, "目标设备数超过上限 " + maxDevices);
        }

        OtaUpgradeTask task = new OtaUpgradeTask();
        task.setUserId(userId);
        task.setFirmwareId(firmware.getId());
        task.setProductId(firmware.getProductId());
        task.setName(request.getName());
        task.setTargetJson(writeTarget(request.getTarget()));
        task.setTotalCount(available.size());
        task.setDispatchedCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setSkippedCount(0);
        task.setDowngradeSkippedCount(downgradeSkipped);
        task.setStatus(OtaStatusValue.TASK_RUNNING);
        task.setBatchMode(canary.batchMode());
        task.setBatchSize(canary.batchSize());
        task.setBatchIntervalMs(canary.batchIntervalMs());
        task.setBatchTotal(canary.batchTotal(available.size()));
        task.setCurrentBatchNo(0);
        taskMapper.insert(task);

        // 落逐台记录（灰度时带 batch_no）与批次行
        List<OtaUpgradeBatch> batches = canary.batched()
                ? createBatches(task, available.size())
                : List.of();
        List<Long> firstWave = createRecords(task, firmware, available, canary.batchSize());

        // 只下发首批；ALL 策略下 firstWave 即全部记录。计数刷新统一交给下发后的
        // refreshAfterDispatch —— 下发前的中间态没有任何读者，提前刷一次只是白写。
        dispatchTask(task.getId(), firstWave);
        refreshAfterDispatch(task.getId(), batches);

        OtaTaskDetailVO vo = detail(userId, task.getId());
        vo.setConflictSkippedCount(versionEligible.size() - available.size());
        vo.setDowngradeSkippedCount(downgradeSkipped);
        return vo;
    }

    @Override
    public IPage<OtaTaskVO> listTasks(Long userId, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<OtaUpgradeTask> result = taskMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<OtaUpgradeTask>lambdaQuery()
                        .eq(OtaUpgradeTask::getUserId, userId)
                        .orderByDesc(OtaUpgradeTask::getCreatedAt)
                        .orderByDesc(OtaUpgradeTask::getId));
        Map<Long, String> productNames = productNames();
        Map<Long, String> versions = firmwareVersions(
                result.getRecords().stream().map(OtaUpgradeTask::getFirmwareId).toList());
        return result.convert(task -> toTaskVO(task, productNames, versions));
    }

    @Override
    public OtaTaskDetailVO detail(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        OtaTaskDetailVO vo = new OtaTaskDetailVO();
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setFirmwareId(task.getFirmwareId());
        vo.setVersion(firmwareVersions(List.of(task.getFirmwareId())).get(task.getFirmwareId()));
        vo.setProductId(task.getProductId());
        vo.setProductName(productNames().get(task.getProductId()));
        vo.setTotalCount(task.getTotalCount());
        vo.setDispatchedCount(task.getDispatchedCount());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setFailedCount(task.getFailedCount());
        vo.setSkippedCount(task.getSkippedCount());
        vo.setDowngradeSkippedCount(task.getDowngradeSkippedCount());
        vo.setStatus(task.getStatus());
        vo.setBatchMode(task.getBatchMode());
        vo.setBatchSize(task.getBatchSize());
        vo.setBatchIntervalMs(task.getBatchIntervalMs());
        vo.setBatchTotal(task.getBatchTotal());
        vo.setCurrentBatchNo(task.getCurrentBatchNo());
        vo.setTarget(readTarget(task.getTargetJson()));
        vo.setBatches(toBatchVOs(batchMapper.selectList(Wrappers.<OtaUpgradeBatch>lambdaQuery()
                .eq(OtaUpgradeBatch::getTaskId, task.getId())
                .orderByAsc(OtaUpgradeBatch::getBatchNo))));
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    @Override
    public IPage<OtaRecordVO> listRecords(Long userId, Long id, String status, long page, long size) {
        requireOwnedTask(userId, id);
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<OtaUpgradeRecord> result = recordMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<OtaUpgradeRecord>lambdaQuery()
                        .eq(OtaUpgradeRecord::getTaskId, id)
                        .eq(status != null && !status.isBlank(), OtaUpgradeRecord::getStatus, status)
                        .orderByAsc(OtaUpgradeRecord::getId));
        Map<Long, Device> devices = devicesById(
                result.getRecords().stream().map(OtaUpgradeRecord::getDeviceId).toList());
        return result.convert(record -> toRecordVO(record, devices));
    }

    @Override
    public List<OtaBatchVO> listBatches(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        return toBatchVOs(batchMapper.selectList(Wrappers.<OtaUpgradeBatch>lambdaQuery()
                .eq(OtaUpgradeBatch::getTaskId, task.getId())
                .orderByAsc(OtaUpgradeBatch::getBatchNo)));
    }

    // ---------- 重投与删除 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retry(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        if (OtaStatusValue.TASK_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID, "任务已全部成功，无法重投");
        }
        if (OtaStatusValue.TASK_ABORTED.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID, "任务已终止，无法重投");
        }
        List<Long> retryable = recordMapper.selectList(Wrappers.<OtaUpgradeRecord>lambdaQuery()
                        .eq(OtaUpgradeRecord::getTaskId, id)
                        .in(OtaUpgradeRecord::getStatus, List.of(OtaStatusValue.RECORD_PENDING,
                                OtaStatusValue.RECORD_FAILED, OtaStatusValue.RECORD_TIMEOUT)))
                .stream().map(OtaUpgradeRecord::getId).toList();
        if (retryable.isEmpty()) {
            return 0;
        }
        // 重置待投：进度归零（否则进度单调不减会挡住设备的新一轮回传），并清空上一轮的下发痕迹，
        // 使超时判定基线回落到本轮的 dispatched_at。校验结论也必须一并清空——
        // 上一轮的 MISMATCHED 是针对上一轮固件写入盘的结论，留着会污染本轮展示。
        recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                .in(OtaUpgradeRecord::getId, retryable)
                .set(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_PENDING)
                .set(OtaUpgradeRecord::getProgress, 0)
                .set(OtaUpgradeRecord::getVerifyStatus, OtaStatusValue.VERIFY_NOT_REPORTED)
                .set(OtaUpgradeRecord::getReportedMd5, null)
                .set(OtaUpgradeRecord::getMessage, null)
                .set(OtaUpgradeRecord::getCommandId, null)
                .set(OtaUpgradeRecord::getDispatchedAt, null)
                .set(OtaUpgradeRecord::getLastReportAt, null));
        // 任务被暂停时重投也一并恢复运行：运维点重投的意图就是「让它继续跑」
        if (OtaStatusValue.TASK_PAUSED.equals(task.getStatus())) {
            taskMapper.update(null, Wrappers.<OtaUpgradeTask>lambdaUpdate()
                    .eq(OtaUpgradeTask::getId, id)
                    .set(OtaUpgradeTask::getStatus, OtaStatusValue.TASK_RUNNING));
        }
        int dispatched = dispatchTask(id, retryable);
        refreshAfterDispatch(id, affectedBatches(task, retryable));
        return dispatched;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        // 暂停中的任务仍有在途设备在刷固件，同样不能删；已终止（ABORTED）的可以删
        if (OtaStatusValue.TASK_RUNNING.equals(task.getStatus())
                || OtaStatusValue.TASK_PAUSED.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID, "任务进行中，无法删除");
        }
        // 记录与批次随任务 ON DELETE CASCADE 物理清理；表均无逻辑删除列，deleteById 即物理删除。
        taskMapper.deleteById(task.getId());
    }

    // ---------- 灰度控制：暂停 / 恢复 / 终止 / 推进（T-32） ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OtaTaskDetailVO pause(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        if (!OtaStatusValue.TASK_RUNNING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID,
                    "仅进行中的任务可暂停，当前状态：" + task.getStatus());
        }
        taskMapper.update(null, Wrappers.<OtaUpgradeTask>lambdaUpdate()
                .eq(OtaUpgradeTask::getId, task.getId())
                .eq(OtaUpgradeTask::getStatus, OtaStatusValue.TASK_RUNNING)
                .set(OtaUpgradeTask::getStatus, OtaStatusValue.TASK_PAUSED));
        log.info("OTA 任务已暂停: taskId={}, currentBatchNo={}", task.getId(), task.getCurrentBatchNo());
        return detail(userId, task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OtaTaskDetailVO resume(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        if (!OtaStatusValue.TASK_PAUSED.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID,
                    "仅已暂停的任务可恢复，当前状态：" + task.getStatus());
        }
        taskMapper.update(null, Wrappers.<OtaUpgradeTask>lambdaUpdate()
                .eq(OtaUpgradeTask::getId, task.getId())
                .eq(OtaUpgradeTask::getStatus, OtaStatusValue.TASK_PAUSED)
                .set(OtaUpgradeTask::getStatus, OtaStatusValue.TASK_RUNNING));
        log.info("OTA 任务已恢复: taskId={}", task.getId());
        return detail(userId, task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int abort(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        if (!OtaStatusValue.TASK_RUNNING.equals(task.getStatus())
                && !OtaStatusValue.TASK_PAUSED.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID,
                    "仅进行中或已暂停的任务可终止，当前状态：" + task.getStatus());
        }
        // 先持状态再改记录：ABORTED 之后补投查询（selectDispatchCandidates 要求 t.status = 'RUNNING'）
        // 立刻不再捞本任务的记录，避免与下面的 SKIPPED 化并发把设备又拉起来下发。
        taskMapper.update(null, Wrappers.<OtaUpgradeTask>lambdaUpdate()
                .eq(OtaUpgradeTask::getId, task.getId())
                .in(OtaUpgradeTask::getStatus, List.of(OtaStatusValue.TASK_RUNNING, OtaStatusValue.TASK_PAUSED))
                .set(OtaUpgradeTask::getStatus, OtaStatusValue.TASK_ABORTED));

        int skipped = recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                .eq(OtaUpgradeRecord::getTaskId, task.getId())
                .eq(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_PENDING)
                .set(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_SKIPPED)
                .set(OtaUpgradeRecord::getMessage, "任务已终止，该设备未下发"));
        // 未下发的批次置 SKIPPED；已 RUNNING 的批次不改，让在途设备跑完再由 refreshBatchCounters 收口
        batchMapper.update(null, Wrappers.<OtaUpgradeBatch>lambdaUpdate()
                .eq(OtaUpgradeBatch::getTaskId, task.getId())
                .eq(OtaUpgradeBatch::getStatus, OtaStatusValue.BATCH_PENDING)
                .set(OtaUpgradeBatch::getStatus, OtaStatusValue.BATCH_SKIPPED)
                .set(OtaUpgradeBatch::getFinishedAt, LocalDateTime.now()));
        taskMapper.refreshCounters(task.getId());
        log.info("OTA 任务已终止: taskId={}, 跳过记录 {} 条", task.getId(), skipped);
        return skipped;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int advance(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        if (!OtaStatusValue.TASK_RUNNING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID,
                    "仅进行中的任务可推进批次，当前状态：" + task.getStatus());
        }
        if (!OtaStatusValue.BATCH_MODE_BATCHED.equals(task.getBatchMode())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID,
                    "该任务为一次性全量下发，无可推进的批次");
        }
        int nextBatchNo = (task.getCurrentBatchNo() == null ? 0 : task.getCurrentBatchNo()) + 1;
        OtaUpgradeBatch next = batchMapper.selectOne(Wrappers.<OtaUpgradeBatch>lambdaQuery()
                .eq(OtaUpgradeBatch::getTaskId, task.getId())
                .eq(OtaUpgradeBatch::getBatchNo, nextBatchNo));
        if (next == null || !OtaStatusValue.BATCH_PENDING.equals(next.getStatus())) {
            return 0;
        }
        return runBatch(next);
    }

    // ---------- 下发与巡检 ----------

    @Override
    public int dispatchTask(Long taskId, List<Long> recordIds) {
        if (taskId == null || recordIds == null || recordIds.isEmpty()) {
            return 0;
        }
        OtaUpgradeTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("OTA 下发跳过：任务不存在, taskId={}", taskId);
            return 0;
        }
        OtaFirmware firmware = firmwareMapper.selectById(task.getFirmwareId());
        if (firmware == null) {
            log.warn("OTA 下发跳过：固件不存在, taskId={}, firmwareId={}", taskId, task.getFirmwareId());
            return 0;
        }
        List<OtaUpgradeRecord> records = recordMapper.selectList(Wrappers.<OtaUpgradeRecord>lambdaQuery()
                .eq(OtaUpgradeRecord::getTaskId, taskId)
                .in(OtaUpgradeRecord::getId, recordIds)
                .eq(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_PENDING));
        if (records.isEmpty()) {
            return 0;
        }
        String paramsJson = buildParams(firmware);

        // G-11-4 下发前二次互斥：建任务时的检查挡不住「两个任务几乎同时创建」的竞态。
        // 一次性批量判定而非逐条查，避免 N+1；冲突设备保持 PENDING 待对方收口后由巡检重投。
        Set<Long> conflicted = new HashSet<>(recordMapper.selectActiveDeviceIdsExcludingTask(
                records.stream().map(OtaUpgradeRecord::getDeviceId).toList(), taskId));

        int dispatched = 0;
        for (OtaUpgradeRecord record : records) {
            if (conflicted.contains(record.getDeviceId())) {
                recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                        .eq(OtaUpgradeRecord::getId, record.getId())
                        .set(OtaUpgradeRecord::getMessage,
                                truncate("设备正在其他升级任务中，待其结束后自动补投")));
                continue;
            }
            if (dispatchOne(task, record, paramsJson)) {
                dispatched++;
            }
        }
        return dispatched;
    }

    @Override
    public int sweepPending(int limit) {
        int safeLimit = Math.max(1, limit);
        List<OtaUpgradeRecord> candidates =
                recordMapper.selectDispatchCandidates(OtaStatusValue.RECORD_PENDING, safeLimit);
        if (candidates.isEmpty()) {
            return 0;
        }
        Map<Long, List<Long>> byTask = new LinkedHashMap<>();
        for (OtaUpgradeRecord record : candidates) {
            byTask.computeIfAbsent(record.getTaskId(), key -> new ArrayList<>()).add(record.getId());
        }
        int dispatched = 0;
        for (Map.Entry<Long, List<Long>> entry : byTask.entrySet()) {
            dispatched += dispatchTask(entry.getKey(), entry.getValue());
            taskMapper.refreshCounters(entry.getKey());
        }
        return dispatched;
    }

    @Override
    public int sweepBatchAdvance(long batchIntervalMs, int limit) {
        if (batchIntervalMs <= 0) {
            return 0;
        }
        List<OtaUpgradeBatch> due = batchMapper.selectDueBatches(LocalDateTime.now(), batchIntervalMs,
                Math.max(1, limit));
        if (due.isEmpty()) {
            return 0;
        }
        int dispatched = 0;
        Set<Long> touchedTasks = new LinkedHashSet<>();
        for (OtaUpgradeBatch batch : due) {
            int sent = runBatch(batch);
            if (sent > 0) {
                dispatched += sent;
            }
            touchedTasks.add(batch.getTaskId());
        }
        for (Long taskId : touchedTasks) {
            refreshAfterDispatch(taskId, List.of());
        }
        if (dispatched > 0) {
            log.info("OTA 灰度推进完成：下发 {} 台，涉及任务 {}", dispatched, touchedTasks);
        }
        return dispatched;
    }

    @Override
    public int sweepTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            return 0;
        }
        LocalDateTime deadline = LocalDateTime.now().minusNanos(timeoutMs * 1_000_000L);
        List<OtaUpgradeRecord> stale = recordMapper.selectList(Wrappers.<OtaUpgradeRecord>lambdaQuery()
                .in(OtaUpgradeRecord::getStatus, List.of(OtaStatusValue.RECORD_DISPATCHED,
                        OtaStatusValue.RECORD_DOWNLOADING, OtaStatusValue.RECORD_FLASHING,
                        OtaStatusValue.RECORD_VERIFYING))
                .apply("COALESCE(last_report_at, dispatched_at) < {0}", deadline));
        if (stale.isEmpty()) {
            return 0;
        }
        Map<Long, Set<Integer>> taskToBatches = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>(stale.size());
        for (OtaUpgradeRecord record : stale) {
            ids.add(record.getId());
            if (record.getTaskId() != null) {
                taskToBatches.computeIfAbsent(record.getTaskId(), key -> new LinkedHashSet<>())
                        .add(record.getBatchNo());
            }
        }
        int updated = recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                .in(OtaUpgradeRecord::getId, ids)
                .set(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_TIMEOUT)
                .set(OtaUpgradeRecord::getMessage, TIMEOUT_MESSAGE));
        for (Map.Entry<Long, Set<Integer>> entry : taskToBatches.entrySet()) {
            taskMapper.refreshCounters(entry.getKey());
            for (Integer batchNo : entry.getValue()) {
                if (batchNo != null) {
                    batchMapper.refreshBatchCounters(entry.getKey(), batchNo);
                }
            }
        }
        return updated;
    }

    // ---------- 下发实现 ----------

    /**
     * 推进一个批次：下发本批并推进任务的 {@code current_batch_no}。
     * <p>
     * 批号推进用 {@code lt(current_batch_no, batch_no)} 守卫而**不是**无条件赋值：
     * 手动推进与自动推进可能并发命中同一批，无条件写会让「推进更快的那一侧」丢掉对方的进度。
     * 即便本批 0 台下发成功也照样推进批号——反复卡在同一批会让整个灰度永远推不动。
     */
    private int runBatch(OtaUpgradeBatch batch) {
        int sent = dispatchBatch(batch);
        taskMapper.update(null, Wrappers.<OtaUpgradeTask>lambdaUpdate()
                .eq(OtaUpgradeTask::getId, batch.getTaskId())
                .lt(OtaUpgradeTask::getCurrentBatchNo, batch.getBatchNo())
                .set(OtaUpgradeTask::getCurrentBatchNo, batch.getBatchNo()));
        return sent;
    }

    /**
     * 下发一个批次：取出该批仍 {@code PENDING} 的记录逐台下发，并把批次置为 {@code RUNNING}。
     * <p>
     * 「置 RUNNING」是无条件写的：即便某台设备全部失败，批次也已经开闸了，
     * 留在 {@code PENDING} 会让灰度推进逻辑下次又挑中同一批。
     */
    private int dispatchBatch(OtaUpgradeBatch batch) {
        List<Long> recordIds = recordMapper.selectList(Wrappers.<OtaUpgradeRecord>lambdaQuery()
                        .eq(OtaUpgradeRecord::getTaskId, batch.getTaskId())
                        .eq(OtaUpgradeRecord::getBatchNo, batch.getBatchNo())
                        .eq(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_PENDING))
                .stream().map(OtaUpgradeRecord::getId).toList();
        int dispatched = recordIds.isEmpty() ? 0 : dispatchTask(batch.getTaskId(), recordIds);
        LocalDateTime now = LocalDateTime.now();
        batchMapper.update(null, Wrappers.<OtaUpgradeBatch>lambdaUpdate()
                .eq(OtaUpgradeBatch::getId, batch.getId())
                .eq(OtaUpgradeBatch::getStatus, OtaStatusValue.BATCH_PENDING)
                .set(OtaUpgradeBatch::getStatus, OtaStatusValue.BATCH_RUNNING)
                .set(OtaUpgradeBatch::getStartedAt, now));
        batchMapper.refreshBatchCounters(batch.getTaskId(), batch.getBatchNo());
        return dispatched;
    }

    /**
     * 单台下发：构造 OTA 服务调用 → {@code invoke} → 成功回写 {@code DISPATCHED}。
     * <p>
     * 单台异常只记该条 {@code message} 并**保持 {@code PENDING}**，等待补投巡检，不阻断整批。
     */
    private boolean dispatchOne(OtaUpgradeTask task, OtaUpgradeRecord record, String paramsJson) {
        try {
            DeviceCommandRecord command = deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                    record.getDeviceId(), task.getProductId(),
                    DeviceCommandService.TYPE_SERVICE, DeviceCommandService.SERVICE_OTA_UPGRADE,
                    paramsJson, DeviceCommandService.CALL_TYPE_ASYNC,
                    DeviceCommandService.SOURCE_OTA, task.getUserId()));
            LocalDateTime now = LocalDateTime.now();
            // last_report_at 置空：把超时判定基线落回本轮的 dispatched_at，避免沿用上一轮的回传时间。
            recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                    .eq(OtaUpgradeRecord::getId, record.getId())
                    .set(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_DISPATCHED)
                    .set(OtaUpgradeRecord::getCommandId, command.getCommandId())
                    .set(OtaUpgradeRecord::getDispatchedAt, now)
                    .set(OtaUpgradeRecord::getLastReportAt, null));
            return true;
        } catch (Exception e) {
            log.warn("OTA 固件下发失败，保持待补投: taskId={}, deviceId={}",
                    task.getId(), record.getDeviceId(), e);
            recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                    .eq(OtaUpgradeRecord::getId, record.getId())
                    .set(OtaUpgradeRecord::getMessage, truncate("下发失败：" + e.getMessage())));
            return false;
        }
    }

    /** OTA 服务入参：{@code {"url","version","md5","size"}}，下行 method 由生成器拼为 {@code thing.service.ota_upgrade}。 */
    private String buildParams(OtaFirmware firmware) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("url", buildDownloadUrl(firmware));
        params.put("version", firmware.getVersion());
        params.put("md5", firmware.getMd5());
        params.put("size", firmware.getFileSize() == null ? 0L : firmware.getFileSize());
        return params.toString();
    }

    /** 设备可访问的固件地址：{@code {public-base-url}/{file_path}}，兼容 base 末尾斜杠。 */
    private String buildDownloadUrl(OtaFirmware firmware) {
        String base = properties.getPublicBaseUrl();
        String path = firmware.getFilePath() == null ? "" : firmware.getFilePath();
        if (base == null || base.isBlank()) {
            return path;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/" + path;
    }

    // ---------- 灰度建模（T-32） ----------

    /**
     * 建任务时解析出的灰度策略。
     *
     * @param batchMode        {@code ALL} / {@code BATCHED}
     * @param batchSize        每批台数；{@code ALL} 恒为 0
     * @param batchIntervalMs  自动推进间隔；{@code ALL} 恒为 0
     */
    private record Canary(String batchMode, int batchSize, long batchIntervalMs) {
        boolean batched() {
            return OtaStatusValue.BATCH_MODE_BATCHED.equals(batchMode);
        }

        int batchTotal(int deviceCount) {
            if (!batched() || deviceCount <= 0) {
                return 1;
            }
            return (deviceCount + batchSize - 1) / batchSize;
        }
    }

    /**
     * 解析并校验灰度参数。
     * <p>
     * 非法 {@code batchMode} 一律拒绝而非归到 {@code ALL}：静默降级会让运维以为在做灰度、
     * 实际全量下发，正是灰度要防的事故。
     */
    private Canary resolveCanary(OtaTaskCreateRequest request) {
        String raw = request.getBatchMode() == null ? "" : request.getBatchMode().trim();
        String mode = raw.isEmpty() ? OtaStatusValue.BATCH_MODE_ALL : raw.toUpperCase();
        if (!OtaStatusValue.BATCH_MODE_ALL.equals(mode) && !OtaStatusValue.BATCH_MODE_BATCHED.equals(mode)) {
            throw new BusinessException(ResultCode.OTA_BATCH_PARAM_INVALID,
                    "不支持的下发策略：" + request.getBatchMode());
        }
        if (OtaStatusValue.BATCH_MODE_ALL.equals(mode)) {
            return new Canary(OtaStatusValue.BATCH_MODE_ALL, 0, 0L);
        }
        int size = request.getBatchSize() != null ? request.getBatchSize() : properties.getDefaultBatchSize();
        if (size <= 0) {
            size = properties.getDefaultBatchSize();
        }
        if (size <= 0) {
            throw new BusinessException(ResultCode.OTA_BATCH_PARAM_INVALID, "每批台数必须大于 0");
        }
        long interval = request.getBatchIntervalMs() == null ? 0L : request.getBatchIntervalMs();
        if (interval < 0) {
            throw new BusinessException(ResultCode.OTA_BATCH_PARAM_INVALID, "批次推进间隔不能为负数");
        }
        return new Canary(OtaStatusValue.BATCH_MODE_BATCHED, size, interval);
    }

    /** 建灰度批次行；{@code scheduledAt} 仅作排期参考与展示，不参与自动推进判定。 */
    private List<OtaUpgradeBatch> createBatches(OtaUpgradeTask task, int deviceCount) {
        int batchSize = task.getBatchSize();
        int total = (deviceCount + batchSize - 1) / batchSize;
        LocalDateTime base = LocalDateTime.now();
        long intervalMs = task.getBatchIntervalMs() == null ? 0L : task.getBatchIntervalMs();
        List<OtaUpgradeBatch> batches = new ArrayList<>(total);
        for (int batchNo = 1; batchNo <= total; batchNo++) {
            OtaUpgradeBatch batch = new OtaUpgradeBatch();
            batch.setTaskId(task.getId());
            batch.setBatchNo(batchNo);
            // 最后一批装余数，批次数与批次容量对不上会让前端进度条出现负数
            batch.setDeviceCount(batchNo == total ? deviceCount - (total - 1) * batchSize : batchSize);
            batch.setDispatchedCount(0);
            batch.setSuccessCount(0);
            batch.setFailedCount(0);
            batch.setSkippedCount(0);
            batch.setStatus(OtaStatusValue.BATCH_PENDING);
            batch.setScheduledAt(batchNo == 1 ? base
                    : base.plusNanos((long) (batchNo - 1) * intervalMs * 1_000_000L));
            batchMapper.insert(batch);
            batches.add(batch);
        }
        return batches;
    }

    /**
     * 落逐台记录；灰度时按批次切片写入并返回**首批**的记录主键（即建任务时应当下发的那些）。
     * <p>
     * {@code ALL} 策略没有批次行，{@code batchNo} 统一为 1（与 V19 存量行一致），
     * 返回值收全部记录主键 —— 对调用方而言「首批」就是全部。
     */
    private List<Long> createRecords(OtaUpgradeTask task, OtaFirmware firmware, List<Device> devices,
                                     int batchSize) {
        boolean batched = batchSize > 0;
        List<Long> allIds = new ArrayList<>(devices.size());
        List<Long> firstWave = new ArrayList<>();
        for (int index = 0; index < devices.size(); index++) {
            Device device = devices.get(index);
            int batchNo = batched ? Math.min(index / batchSize, Integer.MAX_VALUE - 1) + 1 : 1;
            OtaUpgradeRecord record = new OtaUpgradeRecord();
            record.setTaskId(task.getId());
            record.setDeviceId(device.getId());
            record.setFirmwareId(firmware.getId());
            record.setVersion(firmware.getVersion());
            record.setBatchNo(batchNo);
            record.setStatus(OtaStatusValue.RECORD_PENDING);
            record.setProgress(0);
            record.setVerifyStatus(OtaStatusValue.VERIFY_NOT_REPORTED);
            recordMapper.insert(record);
            allIds.add(record.getId());
            if (batchNo == 1) {
                firstWave.add(record.getId());
            }
        }
        return batched ? firstWave : allIds;
    }

    /** 下发后刷新任务与受影响批次的计数。 */
    private void refreshAfterDispatch(Long taskId, Collection<OtaUpgradeBatch> batches) {
        taskMapper.refreshCounters(taskId);
        for (OtaUpgradeBatch batch : batches) {
            if (batch != null && batch.getBatchNo() != null) {
                batchMapper.refreshBatchCounters(taskId, batch.getBatchNo());
            }
        }
    }

    /** 取出给定记录所属的批次号（去重），用于重投后刷新批次计数。 */
    private List<OtaUpgradeBatch> affectedBatches(OtaUpgradeTask task, List<Long> recordIds) {
        if (recordIds.isEmpty()) {
            return List.of();
        }
        Set<Integer> batchNos = recordMapper.selectList(Wrappers.<OtaUpgradeRecord>lambdaQuery()
                        .select(OtaUpgradeRecord::getBatchNo)
                        .in(OtaUpgradeRecord::getId, recordIds))
                .stream().map(OtaUpgradeRecord::getBatchNo)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        List<OtaUpgradeBatch> batches = new ArrayList<>(batchNos.size());
        for (Integer batchNo : batchNos) {
            OtaUpgradeBatch batch = new OtaUpgradeBatch();
            batch.setTaskId(task.getId());
            batch.setBatchNo(batchNo);
            batches.add(batch);
        }
        return batches;
    }

    /**
     * G-11-4：剔除「已在其他升级任务中非终态」的设备。
     * <p>
     * 策略由 {@code app.ota.device-conflict-policy} 决定：{@code SKIP}（默认）静默剔除并继续建任务，
     * {@code REJECT} 则整单拒绝。前者贴合批量运维预期——同一次操作里，已在升级的设备本就不该重复升级。
     * <p>
     * 剔除后可能一台不剩，此时报 {@code 6293} 而非 {@code 6237}：前者能让运维一眼看出是互斥而非配置错。
     */
    /**
     * 降级拦截（T-54）：设备当前固件版本高于目标版本时跳过。
     * <p>
     * 只比较平台已知的 {@code firmware_version}；未知（NULL）的设备放行——
     * 不能用「猜一个当前版本」的方式阻止升级，否则老设备会被永久卡住。
     * {@code allow-downgrade=true} 时整段跳过（现场回滚固件）。
     */
    private List<Device> filterVersionDowngrades(List<Device> matched, String targetVersion) {
        if (properties.isAllowDowngrade() || targetVersion == null || targetVersion.isBlank()) {
            return matched;
        }
        List<Device> eligible = matched.stream()
                .filter(device -> {
                    String current = device.getFirmwareVersion();
                    if (current == null || current.isBlank()) {
                        return true;
                    }
                    if (FirmwareVersionComparator.compare(current, targetVersion) > 0) {
                        log.info("OTA 降级拦截：deviceKey={}, current={}, target={}",
                                device.getDeviceKey(), current, targetVersion);
                        return false;
                    }
                    return true;
                })
                .toList();
        if (eligible.isEmpty() && !matched.isEmpty()) {
            throw new BusinessException(ResultCode.OTA_TASK_INVALID,
                    "目标设备当前固件版本均高于目标版本，已按降级策略拦截（可设置 OTA_ALLOW_DOWNGRADE=true 放开）");
        }
        return eligible;
    }

    private List<Device> excludeConflictingDevices(List<Device> matched) {
        if (matched.isEmpty()) {
            return matched;
        }
        List<Long> deviceIds = matched.stream().map(Device::getId).toList();
        Set<Long> conflicted = new LinkedHashSet<>(
                recordMapper.selectActiveDeviceIdsExcludingTask(deviceIds, NO_TASK_ID));
        if (conflicted.isEmpty()) {
            return matched;
        }
        if ("REJECT".equalsIgnoreCase(properties.getDeviceConflictPolicy())) {
            throw new BusinessException(ResultCode.OTA_DEVICE_CONFLICT,
                    "有 " + conflicted.size() + " 台设备正在其他升级任务中，请先完成或终止相关任务");
        }
        List<Device> available = matched.stream()
                .filter(device -> !conflicted.contains(device.getId()))
                .toList();
        if (available.isEmpty()) {
            throw new BusinessException(ResultCode.OTA_DEVICE_CONFLICT,
                    "目标中的 " + conflicted.size() + " 台设备全部正在其他升级任务中");
        }
        log.info("OTA 建任务剔除冲突设备：目标 {} 台，互斥剔除 {} 台", matched.size(), conflicted.size());
        return available;
    }

    // ---------- 归属与投影 ----------

    private OtaFirmware requireOwnedFirmware(Long userId, Long firmwareId) {
        OtaFirmware firmware = firmwareId == null ? null : firmwareMapper.selectById(firmwareId);
        if (firmware == null || !Objects.equals(firmware.getUserId(), userId)) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_NOT_FOUND);
        }
        return firmware;
    }

    private OtaUpgradeTask requireOwnedTask(Long userId, Long id) {
        OtaUpgradeTask task = id == null ? null : taskMapper.selectById(id);
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            throw new BusinessException(ResultCode.OTA_TASK_NOT_FOUND);
        }
        return task;
    }

    private OtaTaskVO toTaskVO(OtaUpgradeTask task, Map<Long, String> productNames,
                               Map<Long, String> versions) {
        OtaTaskVO vo = new OtaTaskVO();
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setFirmwareId(task.getFirmwareId());
        vo.setVersion(versions.get(task.getFirmwareId()));
        vo.setProductId(task.getProductId());
        vo.setProductName(productNames.get(task.getProductId()));
        vo.setTotalCount(task.getTotalCount());
        vo.setDispatchedCount(task.getDispatchedCount());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setFailedCount(task.getFailedCount());
        vo.setSkippedCount(task.getSkippedCount());
        vo.setDowngradeSkippedCount(task.getDowngradeSkippedCount());
        vo.setStatus(task.getStatus());
        vo.setBatchMode(task.getBatchMode());
        vo.setBatchTotal(task.getBatchTotal());
        vo.setCurrentBatchNo(task.getCurrentBatchNo());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private OtaRecordVO toRecordVO(OtaUpgradeRecord record, Map<Long, Device> devices) {
        OtaRecordVO vo = new OtaRecordVO();
        vo.setDeviceId(record.getDeviceId());
        Device device = devices.get(record.getDeviceId());
        if (device != null) {
            vo.setDeviceName(device.getDeviceName());
            vo.setDeviceKey(device.getDeviceKey());
        }
        vo.setVersion(record.getVersion());
        vo.setBatchNo(record.getBatchNo());
        vo.setStatus(record.getStatus());
        vo.setProgress(record.getProgress());
        vo.setVerifyStatus(record.getVerifyStatus());
        vo.setReportedMd5(record.getReportedMd5());
        vo.setMessage(record.getMessage());
        vo.setCommandId(record.getCommandId());
        vo.setDispatchedAt(record.getDispatchedAt());
        vo.setLastReportAt(record.getLastReportAt());
        return vo;
    }

    private List<OtaBatchVO> toBatchVOs(List<OtaUpgradeBatch> batches) {
        List<OtaBatchVO> vos = new ArrayList<>(batches.size());
        for (OtaUpgradeBatch batch : batches) {
            OtaBatchVO vo = new OtaBatchVO();
            vo.setId(batch.getId());
            vo.setBatchNo(batch.getBatchNo());
            vo.setDeviceCount(batch.getDeviceCount());
            vo.setDispatchedCount(batch.getDispatchedCount());
            vo.setSuccessCount(batch.getSuccessCount());
            vo.setFailedCount(batch.getFailedCount());
            vo.setSkippedCount(batch.getSkippedCount());
            vo.setStatus(batch.getStatus());
            vo.setScheduledAt(batch.getScheduledAt());
            vo.setStartedAt(batch.getStartedAt());
            vo.setFinishedAt(batch.getFinishedAt());
            vos.add(vo);
        }
        return vos;
    }

    /** 产品名映射，供列表 / 详情回填；避免逐条查询造成 N+1。 */
    private Map<Long, String> productNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Product product : productService.list()) {
            names.put(product.getId(), product.getProductName());
        }
        return names;
    }

    private Map<Long, String> firmwareVersions(Collection<Long> firmwareIds) {
        Set<Long> ids = new LinkedHashSet<>();
        firmwareIds.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> versions = new LinkedHashMap<>();
        for (OtaFirmware firmware : firmwareMapper.selectBatchIds(ids)) {
            versions.put(firmware.getId(), firmware.getVersion());
        }
        return versions;
    }

    private Map<Long, Device> devicesById(Collection<Long> deviceIds) {
        Set<Long> ids = new LinkedHashSet<>();
        deviceIds.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Device> devices = new LinkedHashMap<>();
        for (Device device : deviceMapper.selectBatchIds(ids)) {
            devices.put(device.getId(), device);
        }
        return devices;
    }

    private String writeTarget(BatchTargetRequest target) {
        if (target == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(target);
        } catch (Exception e) {
            log.warn("任务目标快照序列化失败", e);
            throw new BusinessException(ResultCode.OTA_TASK_INVALID, "任务目标快照序列化失败");
        }
    }

    private BatchTargetRequest readTarget(String targetJson) {
        if (targetJson == null || targetJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(targetJson, BatchTargetRequest.class);
        } catch (Exception e) {
            log.warn("任务目标快照解析失败: {}", targetJson, e);
            return null;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX);
    }
}