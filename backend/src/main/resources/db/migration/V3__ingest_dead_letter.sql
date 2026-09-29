-- ============================================================
-- V3：上行摄取死信表
-- 用途：队列溢出与批量落库重试耗尽后的消息兜底，保证「不静默丢消息」
-- ============================================================
CREATE TABLE IF NOT EXISTS ingest_dead_letter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key VARCHAR(100) NOT NULL COMMENT '设备标识',
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT Topic',
    message_type VARCHAR(20) NOT NULL COMMENT 'data/heartbeat/lwt',
    payload TEXT COMMENT '消息载荷',
    qos INT DEFAULT 0 COMMENT 'QoS等级',
    received_at DATETIME(3) NOT NULL COMMENT '消息接收时间（毫秒精度）',
    reason VARCHAR(30) NOT NULL COMMENT 'queue_full/persist_failed',
    attempts INT NOT NULL DEFAULT 0 COMMENT '落库尝试次数',
    error_message VARCHAR(500) COMMENT '最后一次失败原因',
    status ENUM('PENDING','REPLAYED','DISCARDED') DEFAULT 'PENDING' COMMENT '处置状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    INDEX idx_status_created (status, created_at),
    INDEX idx_device_key (device_key),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上行摄取死信表';