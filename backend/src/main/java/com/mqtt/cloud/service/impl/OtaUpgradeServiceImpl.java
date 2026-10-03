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
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.dto.response.OtaTaskVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import com.mqtt.cloud.entity.OtaUpgradeTask;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.OtaFirmwareMapper;
import com.mqtt.cloud.mapper.OtaUpgradeRecordMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.OtaUpgradeService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OTA 升级任务服务实现（T-22 设计文档 §7.3）。
 * <p>
 * 目标集合解析复用 {@link DeviceBatchService#resolveTarget}（含产品维度），避免二份解析逻辑分叉；
 * 固件地址下发复用 {@link DeviceCommandService#invoke}（{@code service / ota_upgrade / async / OTA}），
 * 从而免费获得命令记录、回执更新、超时巡检与前端命令历史。
 * <p>
 * 关键语义：
 * <ul>
 *   <li>单台下发失败只写该条 {@code message} 并**保持 {@code PENDING}**，等待补投巡检，不阻断整批；</li>
 *   <li>任务计数与状态不在读路径推算，统一由 {@code refreshCounters} 聚合 {@code ota_upgrade_record} 重算；</li>
 *   <li>{@code ota_upgrade_task} / {@code ota_upgrade_record} 均无逻辑删除列，删除走物理删除。</li>
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

    private final OtaFirmwareMapper firmwareMapper;
    private final OtaUpgradeTaskMapper taskMapper;
    private final OtaUpgradeRecordMapper recordMapper;
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

        // 目标集合：手选 ∪ 产品 ∪ 分组（含子分组）∪ 标签，已按当前用户二次过滤
        List<Device> targets = deviceBatchService.resolveTarget(userId, request.getTarget());
        List<Device> matched = targets.stream()
                .filter(device -> Objects.equals(firmware.getProductId(), device.getProductId()))
                .toList();
        if (matched.isEmpty()) {
            throw new BusinessException(ResultCode.OTA_TASK_INVALID, "目标设备中没有与该固件产品一致的设备");
        }
        int maxDevices = properties.getTaskMaxDevices();
        if (maxDevices > 0 && matched.size() > maxDevices) {
            throw new BusinessException(ResultCode.OTA_TASK_INVALID, "目标设备数超过上限 " + maxDevices);
        }

        OtaUpgradeTask task = new OtaUpgradeTask();
        task.setUserId(userId);
        task.setFirmwareId(firmware.getId());
        task.setProductId(firmware.getProductId());
        task.setName(request.getName());
        task.setTargetJson(writeTarget(request.getTarget()));
        task.setTotalCount(matched.size());
        task.setDispatchedCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setStatus(OtaStatusValue.TASK_RUNNING);
        taskMapper.insert(task);

        List<Long> recordIds = new ArrayList<>(matched.size());
        for (Device device : matched) {
            OtaUpgradeRecord record = new OtaUpgradeRecord();
            record.setTaskId(task.getId());
            record.setDeviceId(device.getId());
            record.setFirmwareId(firmware.getId());
            record.setVersion(firmware.getVersion());
            record.setStatus(OtaStatusValue.RECORD_PENDING);
            record.setProgress(0);
            recordMapper.insert(record);
            recordIds.add(record.getId());
        }

        dispatchTask(task.getId(), recordIds);
        taskMapper.refreshCounters(task.getId());
        return detail(userId, task.getId());
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
        vo.setStatus(task.getStatus());
        vo.setTarget(readTarget(task.getTargetJson()));
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

    // ---------- 重投与删除 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retry(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        if (OtaStatusValue.TASK_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID, "任务已全部成功，无法重投");
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
        // 使超时判定基线回落到本轮的 dispatched_at。
        recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                .in(OtaUpgradeRecord::getId, retryable)
                .set(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_PENDING)
                .set(OtaUpgradeRecord::getProgress, 0)
                .set(OtaUpgradeRecord::getMessage, null)
                .set(OtaUpgradeRecord::getCommandId, null)
                .set(OtaUpgradeRecord::getDispatchedAt, null)
                .set(OtaUpgradeRecord::getLastReportAt, null));
        int dispatched = dispatchTask(id, retryable);
        taskMapper.refreshCounters(id);
        return dispatched;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        OtaUpgradeTask task = requireOwnedTask(userId, id);
        if (OtaStatusValue.TASK_RUNNING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.OTA_TASK_STATE_INVALID, "任务进行中，无法删除");
        }
        // 记录随任务 ON DELETE CASCADE 物理清理；两表均无逻辑删除列，deleteById 即物理删除。
        taskMapper.deleteById(task.getId());
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
        String paramsJson = buildParams(firmware);
        List<OtaUpgradeRecord> records = recordMapper.selectList(Wrappers.<OtaUpgradeRecord>lambdaQuery()
                .eq(OtaUpgradeRecord::getTaskId, taskId)
                .in(OtaUpgradeRecord::getId, recordIds)
                .eq(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_PENDING));
        int dispatched = 0;
        for (OtaUpgradeRecord record : records) {
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
    public int sweepTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            return 0;
        }
        LocalDateTime deadline = LocalDateTime.now().minusNanos(timeoutMs * 1_000_000L);
        List<OtaUpgradeRecord> stale = recordMapper.selectList(Wrappers.<OtaUpgradeRecord>lambdaQuery()
                .in(OtaUpgradeRecord::getStatus, List.of(OtaStatusValue.RECORD_DISPATCHED,
                        OtaStatusValue.RECORD_DOWNLOADING, OtaStatusValue.RECORD_FLASHING))
                .apply("COALESCE(last_report_at, dispatched_at) < {0}", deadline));
        if (stale.isEmpty()) {
            return 0;
        }
        Set<Long> taskIds = new LinkedHashSet<>();
        List<Long> ids = new ArrayList<>(stale.size());
        for (OtaUpgradeRecord record : stale) {
            ids.add(record.getId());
            if (record.getTaskId() != null) {
                taskIds.add(record.getTaskId());
            }
        }
        int updated = recordMapper.update(null, Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                .in(OtaUpgradeRecord::getId, ids)
                .set(OtaUpgradeRecord::getStatus, OtaStatusValue.RECORD_TIMEOUT)
                .set(OtaUpgradeRecord::getMessage, TIMEOUT_MESSAGE));
        for (Long taskId : taskIds) {
            taskMapper.refreshCounters(taskId);
        }
        return updated;
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
        vo.setStatus(task.getStatus());
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
        vo.setStatus(record.getStatus());
        vo.setProgress(record.getProgress());
        vo.setMessage(record.getMessage());
        vo.setCommandId(record.getCommandId());
        vo.setDispatchedAt(record.getDispatchedAt());
        vo.setLastReportAt(record.getLastReportAt());
        return vo;
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
