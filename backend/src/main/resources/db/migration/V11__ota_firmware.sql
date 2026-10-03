-- ============================================================
-- V11：OTA 固件升级（T-22）
-- 用途：
--   1) ota_firmware        固件包元数据（按产品 + 版本唯一，文件落盘共享卷）
--   2) ota_upgrade_task    升级任务（目标快照 + 聚合计数）
--   3) ota_upgrade_record  逐台升级记录（进度回传落点）
-- 说明：只加表，不改既有表，应用版本可安全回滚（回滚后旧代码忽略新表，可用 DROP TABLE 清理）。
--       固件文件落盘到 app.ota.storage-dir（容器内共享卷 ota_firmware），由 frontend nginx 静态托管，
--       设备通过复用既有 /cmd/down 命令通道拿到固件地址；不改 ACL、不新增下行主题。
--       进度由新增上行主题 device/{key}/ota 回传，经 IngestDispatcher 第 6 路旁路落库（异常只记 WARN）。
--       升级历史是运维审计依据，不做自动清理；仅随任务删除级联清理记录。
--       三张表均无逻辑删除列（不受 logic-delete-field: deleted 影响）。
-- ============================================================

CREATE TABLE IF NOT EXISTS ota_firmware (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '上传者（sys_user.id）',
    product_id BIGINT NOT NULL COMMENT '所属产品（product.id）',
    version VARCHAR(64) NOT NULL COMMENT '固件版本号（产品内唯一）',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(512) NOT NULL COMMENT '存储相对路径（{productId}/{version}/{fileName}）',
    file_size BIGINT NOT NULL COMMENT '文件字节数',
    md5 CHAR(32) NOT NULL COMMENT '文件 MD5（小写十六进制），供设备校验',
    description VARCHAR(255) DEFAULT NULL COMMENT '版本说明',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_product_version (product_id, version),
    INDEX idx_user (user_id),
    INDEX idx_product (product_id),
    CONSTRAINT fk_ota_firmware_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_firmware_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OTA 固件包';

CREATE TABLE IF NOT EXISTS ota_upgrade_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '创建者（sys_user.id）',
    firmware_id BIGINT NOT NULL COMMENT '固件包（ota_firmware.id）',
    product_id BIGINT NOT NULL COMMENT '固件所属产品（冗余，便于筛选）',
    name VARCHAR(64) NOT NULL COMMENT '任务名称',
    target_json JSON NOT NULL COMMENT '目标快照（deviceIds/productIds/groupIds/tagIds）',
    total_count INT NOT NULL DEFAULT 0 COMMENT '目标设备总数',
    dispatched_count INT NOT NULL DEFAULT 0 COMMENT '已成功下发数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '升级成功数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '升级失败/超时数',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_firmware (firmware_id),
    CONSTRAINT fk_ota_task_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_task_firmware FOREIGN KEY (firmware_id) REFERENCES ota_firmware(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OTA 升级任务';

CREATE TABLE IF NOT EXISTS ota_upgrade_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '升级任务（ota_upgrade_task.id）',
    device_id BIGINT NOT NULL COMMENT '设备（device.id）',
    firmware_id BIGINT NOT NULL COMMENT '固件包（ota_firmware.id）',
    version VARCHAR(64) NOT NULL COMMENT '目标版本号（冗余）',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING/DISPATCHED/DOWNLOADING/FLASHING/SUCCESS/FAILED/TIMEOUT',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度百分比 0~100',
    message VARCHAR(255) DEFAULT NULL COMMENT '设备回传或系统填写的信息',
    command_id VARCHAR(64) DEFAULT NULL COMMENT '关联命令 ID（device_command_record.command_id）',
    dispatched_at DATETIME(3) DEFAULT NULL COMMENT '最近一次成功下发时间',
    last_report_at DATETIME(3) DEFAULT NULL COMMENT '最近一次进度回传时间',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_task_device (task_id, device_id),
    INDEX idx_device (device_id),
    INDEX idx_status (status),
    INDEX idx_task_status (task_id, status),
    CONSTRAINT fk_ota_record_task FOREIGN KEY (task_id) REFERENCES ota_upgrade_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_record_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OTA 逐台升级记录';
