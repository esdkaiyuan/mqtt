-- ============================================================
-- V6：设备影子（T-16）
-- 用途：
--   1) device_shadow 保存设备云端状态副本（desired / reported / delta + 单调递增 version）
--   2) device_command_record 增加补发相关列（attempt_count / next_attempt_at）与补发索引
-- 说明：只加表 / 只加列，不改既有列，应用版本可安全回滚（无需回滚 V6）。
--       影子不引入新的 MQTT 主题：期望值仍经 device/{deviceKey}/cmd/down 下发，
--       离线时命令置 QUEUED，设备上线后补发，reported 由既有上行属性上报驱动。
-- ============================================================

CREATE TABLE IF NOT EXISTS device_shadow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    desired JSON NULL COMMENT '期望状态：identifier -> 归一化文本',
    reported JSON NULL COMMENT '上报状态：identifier -> 归一化文本',
    delta JSON NULL COMMENT '差异：desired 中与 reported 不一致（含缺失）的键',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '影子版本号，单调递增',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近变更时间',
    UNIQUE KEY uk_shadow_device (device_id),
    CONSTRAINT fk_shadow_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备影子';

ALTER TABLE device_command_record
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 COMMENT '补发尝试次数（QUEUED 阶段累计）',
    ADD COLUMN next_attempt_at DATETIME(3) NULL COMMENT '下次可补发时间（指数退避）';

CREATE INDEX idx_status_next_attempt ON device_command_record (status, next_attempt_at);
