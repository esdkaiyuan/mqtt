-- ============================================================
-- V19：OTA 灰度分批与固件完整性校验（T-32）
-- 用途：
--   1) ota_upgrade_task    增灰度策略与批次进度列；状态扩 PAUSED / ABORTED
--   2) ota_upgrade_record  增所属批次与固件校验结果列；状态扩 VERIFYING / SKIPPED
--   3) ota_upgrade_batch   新建批次表（灰度分批的批次级视图与调度锚点）
-- 对应缺口：G-11-1（灰度分批 / 暂停 / 终止）、G-11-2（固件完整性校验回执）、
--           G-11-4（设备级任务互斥，由 record 的非终态集合参与判定，不新增列）。
-- 说明：
--   - 只加列 / 加表，不改既有列语义，应用版本可安全回滚
--     （回滚后旧代码忽略新列与新表，可 ALTER DROP / DROP TABLE 清理）。
--   - 新列全部带 DEFAULT，存量行回填后与旧行为等价：batch_mode='ALL'（一次性全量，
--     等价于改造前的 createTask 即时全量下发），batch_no=1，batch_total=1，
--     current_batch_no=1，skipped_count=0，verify_status='NOT_REPORTED'。
--   - verify_status 默认 'NOT_REPORTED' 而非 'PASSED'：存量记录在改造前
--     「进度以设备自报为准、未比对 md5」，不得回填成 PASSED 而虚增可信度。
--   - 三张表仍均无逻辑删除列（不受 logic-delete-field: deleted 影响）。
-- ============================================================

ALTER TABLE ota_upgrade_task
    ADD COLUMN batch_mode VARCHAR(16) NOT NULL DEFAULT 'ALL'
        COMMENT '下发策略：ALL=一次性全量 / BATCHED=灰度分批' AFTER status,
    ADD COLUMN batch_size INT NOT NULL DEFAULT 0
        COMMENT '每批台数（BATCHED 时生效，0 表示不分批）' AFTER batch_mode,
    ADD COLUMN batch_interval_ms BIGINT NOT NULL DEFAULT 0
        COMMENT '批次自动推进间隔（毫秒），0=仅手动推进' AFTER batch_size,
    ADD COLUMN batch_total INT NOT NULL DEFAULT 1
        COMMENT '总批次数（ALL 恒为 1）' AFTER batch_interval_ms,
    ADD COLUMN current_batch_no INT NOT NULL DEFAULT 1
        COMMENT '已下发的最大批号（0=尚未下发任何批次）' AFTER batch_total,
    ADD COLUMN skipped_count INT NOT NULL DEFAULT 0
        COMMENT '被终止跳过的设备数（不计入失败）' AFTER failed_count;

CREATE TABLE IF NOT EXISTS ota_upgrade_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '升级任务（ota_upgrade_task.id）',
    batch_no INT NOT NULL COMMENT '批号，从 1 起',
    device_count INT NOT NULL DEFAULT 0 COMMENT '本批设备数',
    dispatched_count INT NOT NULL DEFAULT 0 COMMENT '本批已成功下发数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '本批升级成功数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '本批升级失败/超时数',
    skipped_count INT NOT NULL DEFAULT 0 COMMENT '本批被终止跳过数',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING=待下发 / RUNNING=进行中 / SUCCESS=全成功 / PARTIAL=部分成功 / FAILED=无一成功 / SKIPPED=已跳过',
    scheduled_at DATETIME(3) DEFAULT NULL COMMENT '排期参考时间（批次创建时按批次序号推算，不作为自动推进的判定依据）',
    started_at DATETIME(3) DEFAULT NULL COMMENT '实际下发时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '批次进入终态时间（自动推进间隔的起算点）',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_task_batch (task_id, batch_no),
    INDEX idx_task_status (task_id, status),
    INDEX idx_status (status),
    CONSTRAINT fk_ota_batch_task FOREIGN KEY (task_id) REFERENCES ota_upgrade_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OTA 升级批次';

ALTER TABLE ota_upgrade_record
    ADD COLUMN batch_no INT NOT NULL DEFAULT 1
        COMMENT '所属批号（ota_upgrade_batch.batch_no）' AFTER version,
    ADD COLUMN verify_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REPORTED'
        COMMENT '固件校验：NOT_REPORTED=设备未回传 md5 / VERIFYING=校验中 / PASSED=一致 / MISMATCHED=不一致' AFTER progress,
    ADD COLUMN reported_md5 CHAR(32) DEFAULT NULL
        COMMENT '设备回传的固件 md5（小写十六进制），供与 ota_firmware.md5 比对' AFTER verify_status,
    -- 批次级聚合（refreshBatchCounters）按 (task_id, batch_no) 过滤并按 status 分组计数，
    -- 该组合索引同时覆盖二者；不再另建 idx_task_batch，避免与既有 idx_task_status 冗余。
    ADD INDEX idx_task_batch_status (task_id, batch_no, status);