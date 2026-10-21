package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.common.constant.OtaStatusValue;
import com.mqtt.cloud.config.OtaProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.entity.OtaUpgradeRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.OtaFirmwareMapper;
import com.mqtt.cloud.mapper.OtaUpgradeBatchMapper;
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
 * OTA 升级进度回传处理（T-22 设计文档 §4.5 / §7.3，T-32 增固件完整性校验）。
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
 * <p>
 * <b>固件完整性校验（G-11-2）</b>：设备可在上行报文带 {@code fileMd5}（下载后本地计算的摘要）。
 * 平台与 {@code ota_firmware.md5} 比对，一致记 {@code PASSED}；不一致记 {@code MISMATCHED} 并把记录
 * 改判 {@code FAILED} —— 那说明固件在传输或写盘过程中损坏，绝不能算成功。
 * 设备未回传 md5 时记 {@code NOT_REPORTED}，是否因此判失败由 {@code app.ota.verify-required} 决定
 * （默认放行，保证未升级固件的老设备不会因平台新增能力而全量转红）。
 * <p>
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
            OtaStatusValue.RECORD_VERIFYING,
            OtaStatusValue.RECORD_SUCCESS);

    /** 设备上行状态字面量 → 记录状态。 */
    private static final Map<String, String> DEVICE_STATUS_MAPPING = Map.of(
            OtaStatusValue.DEVICE_DOWNLOADING, OtaStatusValue.RECORD_DOWNLOADING,
            OtaStatusValue.DEVICE_FLASHING, OtaStatusValue.RECORD_FLASHING,
            OtaStatusValue.DEVICE_VERIFYING, OtaStatusValue.RECORD_VERIFYING,
            OtaStatusValue.DEVICE_SUCCESS, OtaStatusValue.RECORD_SUCCESS,
            OtaStatusValue.DEVICE_FAILED, OtaStatusValue.RECORD_FAILED);

    private static final String MESSAGE_TYPE_OTA = "ota";

    /** 单条记录 {@code message} 长度上限（与下行错误文案截断保持一致）。 */
    private static final int MESSAGE_MAX = 255;

    /**
     * 「下载完成、尚未刷写」对应的保守进度（T-32）。
     * <p>
     * 用于两处兜底：设备报 success 却没给 md5 时不让进度跳到 100；校验不一致时把进度压回此处，
     * 避免详情页出现「进度 100% 但状态失败」这种自相矛盾的展示。
     */
    private static final int PROGRESS_AFTER_DOWNLOAD = 90;

    private final OtaUpgradeRecordMapper recordMapper;
    private final OtaUpgradeTaskMapper taskMapper;
    private final OtaUpgradeBatchMapper batchMapper;
    private final OtaFirmwareMapper firmwareMapper;
    private final OtaProperties properties;
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
     * 单条进度落库（T-22 设计文档 §7.3，T-32 增校验）。
     * <p>
     * 解析 → 归位 → 版本校验 → 固件校验 → 状态机推进 → 更新落库 → 终态时重算任务与批次计数。
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

        Verification verification = verify(record, payload);

        // 分支一：完整性校验不一致。无论设备报的是 success 还是 flashing，一律改判失败。
        if (verification.mismatched()) {
            writeProgress(record, OtaStatusValue.RECORD_FAILED, cappedProgress(record.getProgress()),
                    verification.message(), verification);
            refreshCounters(record);
            log.warn("OTA 固件校验不一致，已判失败: deviceKey={}, taskId={}, expect={}, actual={}",
                    device.getDeviceKey(), record.getTaskId(),
                    verification.expectedMd5(), verification.reportedMd5());
            return;
        }

        String nextStatus = resolveNextStatus(record.getStatus(), targetStatus);

        // 分支二：设备声称成功，但平台拿不到可判定的校验结论，且运维要求强制校验 → 判失败。
        if (verification.required() && OtaStatusValue.RECORD_SUCCESS.equals(nextStatus)) {
            writeProgress(record, OtaStatusValue.RECORD_FAILED,
                    cappedProgress(record.getProgress()), verification.message(), verification);
            refreshCounters(record);
            return;
        }

        boolean terminal = OtaStatusValue.TERMINAL_STATUSES.contains(nextStatus);
        writeProgress(record, nextStatus, resolveProgress(record.getProgress(), payload.progress()),
                payload.message(), verification);
        if (terminal) {
            refreshCounters(record);
        }
    }

    /**
     * 固件校验判定结果。
     *
     * @param mismatched   是否不一致（不一致即判失败，与设备上报状态无关）
     * @param required     设备声称成功但平台无法确认完整性，且运维要求强制校验
     * @param verifyStatus 要写入的 {@code verify_status}；{@code null} 表示本次不改动该列
     * @param reportedMd5  要写入的 {@code reported_md5}；{@code null} 表示设备未回传
     * @param expectedMd5  平台侧期望的 md5，仅用于失败文案与日志
     * @param message      失败文案；成功路径为 {@code null}
     */
    private record Verification(boolean mismatched, boolean required, String verifyStatus,
                                String reportedMd5, String expectedMd5, String message) {
        /** 本次不涉及校验：不动 {@code verify_status} / {@code reported_md5}。 */
        static Verification skip() {
            return new Verification(false, false, null, null, null, null);
        }
    }

    /**
     * 依据设备回传的 md5 与 {@code ota_firmware.md5} 做一致性判定。
     * <p>
     * 只在「设备报了 success」或「设备报了 verifying」时才动作：其余状态下发的进度与校验无关，
     * 不应写入 {@code verify_status}，否则会把已 {@code PASSED} 的记录又写回 {@code VERIFYING}。
     */
    private Verification verify(OtaUpgradeRecord record, OtaProgressPayload payload) {
        boolean declaringSuccess = OtaStatusValue.DEVICE_SUCCESS.equals(payload.status());
        boolean declaringVerifying = OtaStatusValue.DEVICE_VERIFYING.equals(payload.status());
        if (!declaringSuccess && !declaringVerifying) {
            return Verification.skip();
        }

        String reported = payload.fileMd5();
        String expected = expectedMd5(record.getFirmwareId());

        if (reported == null) {
            if (declaringVerifying) {
                // 报 verifying 但给不出 md5：只能确认「正在校验」，落 VERIFYING 等后续 success
                return new Verification(false, false, OtaStatusValue.VERIFY_VERIFYING, null, expected, null);
            }
            // 报 success 却没给 md5
            boolean strict = properties.isVerifyRequired();
            return new Verification(false, strict, OtaStatusValue.VERIFY_NOT_REPORTED, null, expected,
                    strict ? "设备未回传固件校验结果，无法确认升级完整性" : null);
        }

        if (expected == null) {
            // 平台侧固件记录异常（firmware_id 有外键，理论上不会发生）。不因此判失败，但要留痕。
            log.warn("OTA 校验跳过：固件记录不存在, firmwareId={}", record.getFirmwareId());
            return new Verification(false, false, OtaStatusValue.VERIFY_NOT_REPORTED, reported, null, null);
        }

        if (expected.equalsIgnoreCase(reported)) {
            return new Verification(false, false, OtaStatusValue.VERIFY_PASSED, reported, expected, null);
        }
        return new Verification(true, true, OtaStatusValue.VERIFY_MISMATCHED, reported, expected,
                truncate("固件完整性校验不通过：期望 md5=" + expected + "，设备回传=" + reported));
    }

    /** 取平台侧期望的固件 md5；固件不存在时返回 {@code null}。 */
    private String expectedMd5(Long firmwareId) {
        if (firmwareId == null) {
            return null;
        }
        OtaFirmware firmware = firmwareMapper.selectById(firmwareId);
        return firmware == null ? null : firmware.getMd5();
    }

    /**
     * 写入记录。{@code verification} 非空且带值时才动 {@code verify_status} / {@code reported_md5}，
     * 避免无关状态的上行报文把已有校验结论覆盖掉。
     * <p>
     * {@code progress} 由调用方保证非 {@code null}：{@code lambdaUpdate.set()} 传 {@code null}
     * 会真的生成 {@code SET progress = NULL}，把已有进度抹掉。
     */
    private void writeProgress(OtaUpgradeRecord record, String status, Integer progress, String message,
                               Verification verification) {
        var update = Wrappers.<OtaUpgradeRecord>lambdaUpdate()
                .eq(OtaUpgradeRecord::getId, record.getId())
                .set(OtaUpgradeRecord::getStatus, status)
                .set(OtaUpgradeRecord::getProgress, progress)
                .set(OtaUpgradeRecord::getLastReportAt, LocalDateTime.now());
        if (message != null) {
            update.set(OtaUpgradeRecord::getMessage, truncate(message));
        }
        if (verification != null && verification.verifyStatus() != null) {
            update.set(OtaUpgradeRecord::getVerifyStatus, verification.verifyStatus());
        }
        if (verification != null && verification.reportedMd5() != null) {
            update.set(OtaUpgradeRecord::getReportedMd5, verification.reportedMd5());
        }
        recordMapper.update(null, update);
    }

    /**
     * 记录进入终态时重算任务与批次计数。
     * <p>
     * 批次刷新不能省：灰度自动推进以「上一批已无非终态记录」为前提，
     * 批次计数若滞后，下一批会被多压后一轮巡检。
     */
    private void refreshCounters(OtaUpgradeRecord record) {
        taskMapper.refreshCounters(record.getTaskId());
        if (record.getBatchNo() != null) {
            batchMapper.refreshBatchCounters(record.getTaskId(), record.getBatchNo());
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

    /**
     * 校验类失败时使用的进度：压到「已下载完成」及以下。
     * <p>
     * 此处是全类唯一允许进度回退的分支，且只发生在记录即将进入 {@code FAILED} 终态时 ——
     * 终态记录不再接受任何回传，因此不存在乱序覆盖风险；用命中的换来「进度与状态自洽」的展示。
     */
    private Integer cappedProgress(Integer currentProgress) {
        int base = currentProgress == null ? 0 : currentProgress;
        return Math.min(base, PROGRESS_AFTER_DOWNLOAD);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MESSAGE_MAX ? message : message.substring(0, MESSAGE_MAX);
    }
}