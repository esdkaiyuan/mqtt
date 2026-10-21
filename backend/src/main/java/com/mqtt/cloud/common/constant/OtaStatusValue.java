package com.mqtt.cloud.common.constant;

import java.util.Set;

/**
 * OTA 升级状态取值（T-22 建，T-32 扩），与 {@code ota_upgrade_task.status} /
 * {@code ota_upgrade_record.status} / {@code ota_upgrade_batch.status} 一致。
 * <p>
 * 记录（{@code ota_upgrade_record}）状态机：
 * {@code PENDING → DISPATCHED → DOWNLOADING → FLASHING → SUCCESS}，旁支 {@code FAILED} / {@code TIMEOUT}；
 * T-32 追加 {@code VERIFYING}（下载完成、校验固件 md5 中，插在 {@code FLASHING} 与 {@code SUCCESS} 之间）
 * 与 {@code SKIPPED}（任务终止时未下发即被跳过）。
 * <p>
 * 任务（{@code ota_upgrade_task}）状态由记录聚合重算：{@code RUNNING} / {@code SUCCESS} / {@code PARTIAL} / {@code FAILED}；
 * T-32 追加两个**由操作而非聚合产生**的中间态 {@code PAUSED} / {@code ABORTED} ——
 * 这两个状态下 {@code refreshCounters} 必须保留原状态不被聚合覆盖（见其 CASE 首个分支）。
 * <p>
 * 批次（{@code ota_upgrade_batch}）状态：{@code PENDING} / {@code RUNNING} / {@code SUCCESS} / {@code PARTIAL} /
 * {@code FAILED} / {@code SKIPPED}。
 */
public final class OtaStatusValue {

    // ===== 记录状态 =====
    /** 待下发（已建记录，尚未成功下发命令） */
    public static final String RECORD_PENDING = "PENDING";
    /** 已下发（命令已成功投递到 /cmd/down） */
    public static final String RECORD_DISPATCHED = "DISPATCHED";
    /** 设备下载固件中 */
    public static final String RECORD_DOWNLOADING = "DOWNLOADING";
    /** 设备刷写固件中 */
    public static final String RECORD_FLASHING = "FLASHING";
    /** 下载完成、校验固件完整性中（T-32 新增） */
    public static final String RECORD_VERIFYING = "VERIFYING";
    /** 升级成功 */
    public static final String RECORD_SUCCESS = "SUCCESS";
    /** 升级失败 */
    public static final String RECORD_FAILED = "FAILED";
    /** 超时（长时间无进度回传） */
    public static final String RECORD_TIMEOUT = "TIMEOUT";
    /** 任务终止时尚未下发而被跳过（T-32 新增，**不计入失败**） */
    public static final String RECORD_SKIPPED = "SKIPPED";

    // ===== 任务状态 =====
    /** 存在非终态记录 */
    public static final String TASK_RUNNING = "RUNNING";
    /** 全部记录成功 */
    public static final String TASK_SUCCESS = "SUCCESS";
    /** 部分成功、部分失败 */
    public static final String TASK_PARTIAL = "PARTIAL";
    /** 无一成功 */
    public static final String TASK_FAILED = "FAILED";
    /** 已暂停灰度下发（在途设备继续跑完，不再开新批；T-32 新增） */
    public static final String TASK_PAUSED = "PAUSED";
    /** 已终止（未下发记录置 SKIPPED，在途记录不再回填成功；T-32 新增） */
    public static final String TASK_ABORTED = "ABORTED";

    // ===== 批次状态 =====
    /** 批次待下发 */
    public static final String BATCH_PENDING = "PENDING";
    /** 批次进行中（存在非终态记录） */
    public static final String BATCH_RUNNING = "RUNNING";
    /** 批次全成功 */
    public static final String BATCH_SUCCESS = "SUCCESS";
    /** 批次部分成功 */
    public static final String BATCH_PARTIAL = "PARTIAL";
    /** 批次无一成功 */
    public static final String BATCH_FAILED = "FAILED";
    /** 批次随任务终止被跳过（T-32 新增） */
    public static final String BATCH_SKIPPED = "SKIPPED";

    // ===== 下发策略 =====
    /** 一次性全量下发（T-22 既有行为，V19 存量行的默认值） */
    public static final String BATCH_MODE_ALL = "ALL";
    /** 灰度分批下发（T-32 新增） */
    public static final String BATCH_MODE_BATCHED = "BATCHED";

    // ===== 固件校验结果 =====
    /** 设备未回传 md5（T-22 存量记录的默认值，不得回填为 PASSED） */
    public static final String VERIFY_NOT_REPORTED = "NOT_REPORTED";
    /** 校验中（设备已回传 verifying） */
    public static final String VERIFY_VERIFYING = "VERIFYING";
    /** 设备回传 md5 与固件一致 */
    public static final String VERIFY_PASSED = "PASSED";
    /** 设备回传 md5 与固件不一致，记录已置 FAILED */
    public static final String VERIFY_MISMATCHED = "MISMATCHED";

    // ===== 设备上行状态字面量（device/{key}/ota 报文中的 status） =====
    public static final String DEVICE_DOWNLOADING = "downloading";
    public static final String DEVICE_FLASHING = "flashing";
    /** 设备已下载完成、校验中（T-32 新增） */
    public static final String DEVICE_VERIFYING = "verifying";
    public static final String DEVICE_SUCCESS = "success";
    public static final String DEVICE_FAILED = "failed";

    /** 非终态记录状态：仍需继续下发或等待进度回传。 */
    public static final Set<String> NON_TERMINAL_STATUSES =
            Set.of(RECORD_PENDING, RECORD_DISPATCHED, RECORD_DOWNLOADING, RECORD_FLASHING, RECORD_VERIFYING);

    /** 终态记录状态：不会再变化。 */
    public static final Set<String> TERMINAL_STATUSES =
            Set.of(RECORD_SUCCESS, RECORD_FAILED, RECORD_TIMEOUT, RECORD_SKIPPED);

    /**
     * 由操作产生、聚合不得覆盖的任务状态（T-32 新增）。
     * <p>
     * {@code PAUSED} / {@code ABORTED} 表达的是**运维意图**而非记录聚合结果，
     * 若被 {@code refreshCounters} 按记录状态重算，暂停中的任务会被立刻改回 {@code RUNNING}，
     * 终止中的任务会被改回 {@code SUCCESS/FAILED}，两个操作因此失效。
     */
    public static final Set<String> OPERATOR_HELD_TASK_STATUSES = Set.of(TASK_PAUSED, TASK_ABORTED);

    /**
     * 「已下发」判定：排除 {@code PENDING}（未下发）与 {@code SKIPPED}（终止跳过，从未下发）。
     * <p>
     * T-22 口径为 {@code status <> 'PENDING'}，T-32 加入 {@code SKIPPED} 后必须同步排除，
     * 否则终止任务的 {@code dispatched_count} 会虚高到 {@code total_count}。
     */
    public static final String DISPATCHED_WHERE_SQL =
            "status <> 'PENDING' AND status <> 'SKIPPED'";

    private OtaStatusValue() {
    }
}
