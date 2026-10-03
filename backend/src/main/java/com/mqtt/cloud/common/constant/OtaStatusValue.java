package com.mqtt.cloud.common.constant;

import java.util.Set;

/**
 * OTA 升级状态取值（T-22），与 {@code ota_upgrade_task.status} / {@code ota_upgrade_record.status} 一致。
 * <p>
 * 记录（{@code ota_upgrade_record}）状态机：
 * {@code PENDING → DISPATCHED → DOWNLOADING → FLASHING → SUCCESS}，旁支 {@code FAILED} / {@code TIMEOUT}。
 * 任务（{@code ota_upgrade_task}）状态由记录聚合重算：{@code RUNNING} / {@code SUCCESS} / {@code PARTIAL} / {@code FAILED}。
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
    /** 升级成功 */
    public static final String RECORD_SUCCESS = "SUCCESS";
    /** 升级失败 */
    public static final String RECORD_FAILED = "FAILED";
    /** 超时（长时间无进度回传） */
    public static final String RECORD_TIMEOUT = "TIMEOUT";

    // ===== 任务状态 =====
    /** 存在非终态记录 */
    public static final String TASK_RUNNING = "RUNNING";
    /** 全部记录成功 */
    public static final String TASK_SUCCESS = "SUCCESS";
    /** 部分成功、部分失败 */
    public static final String TASK_PARTIAL = "PARTIAL";
    /** 无一成功 */
    public static final String TASK_FAILED = "FAILED";

    // ===== 设备上行状态字面量（device/{key}/ota 报文中的 status） =====
    public static final String DEVICE_DOWNLOADING = "downloading";
    public static final String DEVICE_FLASHING = "flashing";
    public static final String DEVICE_SUCCESS = "success";
    public static final String DEVICE_FAILED = "failed";

    /** 非终态记录状态：仍需继续下发或等待进度回传。 */
    public static final Set<String> NON_TERMINAL_STATUSES =
            Set.of(RECORD_PENDING, RECORD_DISPATCHED, RECORD_DOWNLOADING, RECORD_FLASHING);

    /** 终态记录状态：不会再变化。 */
    public static final Set<String> TERMINAL_STATUSES =
            Set.of(RECORD_SUCCESS, RECORD_FAILED, RECORD_TIMEOUT);

    private OtaStatusValue() {
    }
}
