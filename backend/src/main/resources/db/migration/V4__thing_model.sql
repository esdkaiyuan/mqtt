-- ============================================================
-- V4：物模型（TSL）与数据解析派生表
-- 用途：
--   1) product 承载物模型 JSON 与版本号
--   2) device_property_latest 保存按物模型解析出的属性最新值
--   3) device_event_record 保存按物模型解析出的事件记录
-- 说明：只加列 / 加表，不改既有列，应用版本可安全回滚。
-- ============================================================

-- 1. 产品物模型字段（JSON 类型与 device.metadata 保持一致）
ALTER TABLE product ADD COLUMN thing_model JSON NULL COMMENT '物模型 TSL JSON';
ALTER TABLE product ADD COLUMN thing_model_version INT NOT NULL DEFAULT 0 COMMENT '物模型版本号，每次保存 +1';
ALTER TABLE product ADD COLUMN thing_model_updated_at DATETIME NULL COMMENT '物模型最后保存时间';

-- 2. 设备属性最新值（只保留最新一条，时序数据归 T-21）
CREATE TABLE IF NOT EXISTS device_property_latest (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    identifier VARCHAR(64) NOT NULL COMMENT '属性标识符',
    data_type VARCHAR(16) NOT NULL COMMENT '数据类型（落库时的物模型定义）',
    value_text TEXT COMMENT '属性值（统一文本存储，按 data_type 解释）',
    reported_at DATETIME(3) NOT NULL COMMENT '设备上报时间（毫秒精度）',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '云端更新时间',
    UNIQUE KEY uk_device_identifier (device_id, identifier),
    INDEX idx_identifier (identifier),
    CONSTRAINT fk_prop_latest_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备属性最新值';

-- 3. 设备事件记录（追加型，不去重；去重属告警中心抑制窗口职责）
CREATE TABLE IF NOT EXISTS device_event_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    identifier VARCHAR(64) NOT NULL COMMENT '事件标识符',
    event_type VARCHAR(16) NOT NULL COMMENT 'info/alert/fault（落库时的物模型定义）',
    output_data JSON COMMENT '事件输出参数',
    reported_at DATETIME(3) NOT NULL COMMENT '设备上报时间（毫秒精度）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    INDEX idx_device_reported (device_id, reported_at),
    INDEX idx_identifier (identifier),
    CONSTRAINT fk_event_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备事件记录';