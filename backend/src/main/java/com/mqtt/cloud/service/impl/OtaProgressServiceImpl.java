package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.common.constant.OtaStatusValue;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.OtaUpgradeRecordMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.OtaProgressPayload;
import com.mqtt.cloud.service.OtaProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OTA 升级进度回传处理（T-22 设计文档 §4.5 / §7.3）。
 * <p>
 * 由 {@code IngestDispatcher} 在落库事务提交后作为第 6 路 fan-out 旁路调用，只处理
 * {@code messageType=ota} 的事件：解析设备上行 {@code device/{key}/ota} 报文，归位到该设备
 * 最近一条非终态升级记录并推进状态机。
 * <p>
 * 约定：
 * <ul>
 *   <li>逐条隔离异常，绝不冒泡 —— 冒泡会导致 worker 整批重试、消息重复落库。</li>
 *   <li>进度单调不减：设备回传的 {@code progress} 小于当前值时不回退。</li>
 *   <li>状态只允许前向迁移；{@code failed} 可从任意非终态直达 {@code FAILED}。</li>
 *   <li>版本不一致、报文非法、无归属记录（如手工升级）一律静默忽略。</li>
 * </ul>
 * 持久化细节集中在本类（ArchUnit 规则 4：接口层不得依赖 {@code ..mapper..}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaProgressServiceImpl implements OtaProgressService {

    /** 记录状态前向迁移序列，索引即优先级；不在序列内的状态（如已被巡检置 {@code TIMEOUT}）不参与迁移。 */
    private static final List<String> FORWARD_SEQUENCE = List.of(
            OtaStatusValue.RECORD_PENDING,
            OtaStatusValue.RECORD_DISPATCHED,
            OtaStatusValue.RECORD_DOWNLOADING,
            OtaStatusValue.RECORD_FLASHING,
            OtaStatusValue.RECORD_SUCCESS);

    /** 设备上行状态字面量 → 记录状态。 */
    private static final Map<String, String> DEVICE_STATUS_MAPPING = Map.of(
            OtaStatusValue.DEVICE_DOWNLOADING, OtaStatusValue.RECORD_DOWNLOADING,
            OtaStatusValue.DEVICE_FLASHING, OtaStatusValue.RECORD_FLASHING,
            OtaStatusValue.DEVICE_SUCCESS, OtaStatusValue.RECORD_SUCCESS,
            OtaStatusValue.DEVICE_FAILED, OtaStatusValue.RECORD_FAILED);

    private static final String MESSAGE_TYPE_OTA = "ota";

    private final OtaUpgradeRecordMapper recordMapper;
    private final OtaUpgradeTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(List<ResolvedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (ResolvedEvent event : events) {
            if (event == null || event.device() == null || event.record() == null) {
                continue;
            }
            if (!MESSAGE_TYPE_OTA.equals(event.record().messageType())) {
                continue;
            }
            try {
                applyProgress(event.device(), event.record().payload());
            } catch (Exception e) {
                log.warn("OTA 进度落库失败: deviceKey={}", event.device().getDeviceKey(), e);
            }
        }
    }

    /**
     * 单条进度落库（T-22 设计文档 §7.3）。
     * <p>
     * 解析 → 归位 → 版本校验 → 状态机推进 → 更新落库 → 终态时重算任务计数。
     */
    private void applyProgress(Device device, String rawPayload) {
        OtaProgressPayload payload = OtaProgressPayload.parse(objectMapper, rawPayload);
        if (payload == null) {
            log.warn("OTA 进度报文非法，已丢弃: deviceKey={}", device.getDeviceKey());
            return;
        }
        OtaUpgradeRecord record = recordMapper.selectLatestActiveByDevice(device.getId());
        if (record == null) {
            log.debug("OTA 进度无归属记录（可能为手工升级）: deviceKey={}, version={}",
                    device.getDeviceKey(), payload.version());
            return;
        }
        if (OtaStatusValue.TERMINAL_STATUSES.contains(record.getStatus())) {
            return;
        }
        if (!Objects.equals(record.getVersion(), payload.version())) {
            log.warn("OTA 进度版本不匹配，已忽略: deviceKey={}, expect={}, actual={}",
                    device.getDeviceKey(), record.getVersion(), payload.version());
            return;
        }
        String targetStatus = DEVICE_STATUS_MAPPING.get(payload.status());
        if (targetStatus == null) {
            return;
        }
        String nextStatus = resolveNextStatus(record.getStatus(), targetStatus);
        boolean terminal = OtaStatusValue.TERMINAL_STATUSES.contains(nextStatus);
        Integer nextProgress = resolveProgress(record.getProgress(), payload.progress());

        var update = Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                .eq(OtaUpgradeRecord::getId, record.getId())
                .set(OtaUpgradeRecord::getStatus, nextStatus)
                .set(OtaUpgradeRecord::getProgress, nextProgress)
                .set(OtaUpgradeRecord::getLastReportAt, LocalDateTime.now());
        if (payload.message() != null) {
            update.set(OtaUpgradeRecord::getMessage, payload.message());
        }
        recordMapper.update(null, update);

        if (terminal) {
            taskMapper.refreshCounters(record.getTaskId());
        }
    }

    /**
     * 解析目标记录状态：{@code failed} 直达 {@code FAILED}；其余仅当前向迁移（目标优先级更高）时采纳，
     * 否则保持原状态（含设备乱序回传的旧状态）。
     */
    private String resolveNextStatus(String currentStatus, String targetStatus) {
        if (OtaStatusValue.RECORD_FAILED.equals(targetStatus)) {
            return OtaStatusValue.RECORD_FAILED;
        }
        int currentIndex = FORWARD_SEQUENCE.indexOf(currentStatus);
        int targetIndex = FORWARD_SEQUENCE.indexOf(targetStatus);
        if (currentIndex < 0 || targetIndex < 0) {
            return currentStatus;
        }
        return targetIndex > currentIndex ? targetStatus : currentStatus;
    }

    /** 进度单调不减：设备本次未上报则保持当前值，上报低于当前值时取当前值。 */
    private Integer resolveProgress(Integer currentProgress, Integer reportedProgress) {
        int base = currentProgress == null ? 0 : currentProgress;
        if (reportedProgress == null) {
            return base;
        }
        return Math.max(base, reportedProgress);
    }
}
