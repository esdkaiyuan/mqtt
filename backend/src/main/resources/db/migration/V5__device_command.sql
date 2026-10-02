-- ============================================================
-- V5：命令下发与服务调用（T-15）
-- 用途：
--   1) device_command_record 保存每次下发的命令记录（属性设置 / 服务调用）
--   2) 记录状态流转 PENDING → SENT → ACKED/FAILED/TIMEOUT，作为命令全生命周期的唯一载体
-- 说明：只加表，不改既有列，应用版本可安全回滚。
--       下行主题 device/{deviceKey}/cmd/down，上行回执 device/{deviceKey}/reply，
--       命令关联键为 Alink 载荷的 id（即本表 command_id）。
-- ============================================================

CREATE TABLE IF NOT EXISTS device_command_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_id VARCHAR(36) NOT NULL COMMENT '命令唯一标识（UUID），回执关联键',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    product_id BIGINT NULL COMMENT '下发时的产品ID（冗余，便于按产品统计）',
    command_type VARCHAR(16) NOT NULL COMMENT 'property_set / service',
    identifier VARCHAR(64) NULL COMMENT '服务标识符；property_set 为 NULL',
    params JSON NULL COMMENT '请求参数原文',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING / SENT / ACKED / FAILED / TIMEOUT',
    call_type VARCHAR(8) NOT NULL COMMENT 'sync / async',
    source VARCHAR(16) NOT NULL COMMENT 'CONSOLE / OPEN_API',
    operator_id BIGINT NULL COMMENT '控制台操作者用户ID；开放 API 为 NULL',
    result JSON NULL COMMENT '回执 data',
    error_message VARCHAR(255) NULL COMMENT '失败原因（发布失败 / 回执非 200 / 超时）',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（毫秒精度，用于耗时计算）',
    sent_at DATETIME(3) NULL COMMENT '发布到 Broker 的时间',
    finished_at DATETIME(3) NULL COMMENT '终态时间（ACKED/FAILED/TIMEOUT）',
    UNIQUE KEY uk_command_id (command_id),
    INDEX idx_device_created (device_id, created_at),
    INDEX idx_status_created (status, created_at),
    CONSTRAINT fk_command_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备命令记录';