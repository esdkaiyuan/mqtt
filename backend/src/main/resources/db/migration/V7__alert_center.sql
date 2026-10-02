-- ============================================================
-- V7：告警中心（T-17）
-- 用途：
--   1) alert_rule   用户配置的告警规则（阈值 / 离线 / 事件三类来源）
--   2) alert_record 告警记录（三态流转 + 抑制窗口计数）
-- 说明：只加表，不改既有表，应用版本可安全回滚（回滚后新旧表共存，旧代码忽略新表）。
--       告警中心完全运行在服务端：数据来源为已落库的解析产物
--       （device_property_latest / device_event_record）与设备运行态（device.status / last_seen），
--       通知出口复用既有 Webhook（HTTP 回调），不新增任何 MQTT 主题。
-- ============================================================

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '规则归属用户',
    device_id BIGINT NULL COMMENT '作用设备ID，NULL=该用户全部设备',
    name VARCHAR(64) NOT NULL COMMENT '规则名称',
    source_type VARCHAR(16) NOT NULL COMMENT '来源：THRESHOLD/OFFLINE/EVENT',
    severity VARCHAR(16) NOT NULL DEFAULT 'WARNING' COMMENT '级别：INFO/WARNING/CRITICAL',
    identifier VARCHAR(64) NULL COMMENT '属性/事件标识符（THRESHOLD/EVENT 必填）',
    operator VARCHAR(8) NULL COMMENT '比较符：GT/GTE/LT/LTE/EQ/NE（THRESHOLD 必填）',
    threshold_value VARCHAR(255) NULL COMMENT '阈值（归一化文本，THRESHOLD 必填）',
    event_type VARCHAR(16) NULL COMMENT '事件类型过滤：info/alert/fault（EVENT 可选）',
    offline_seconds INT NOT NULL DEFAULT 0 COMMENT '离线持续阈值（秒，OFFLINE 用，0=立即）',
    suppress_window_seconds INT NOT NULL DEFAULT 0 COMMENT '抑制窗口（秒，0=用全局默认）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1/0',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：1/0',
    INDEX idx_rule_user (user_id, enabled),
    INDEX idx_rule_source (source_type, enabled),
    INDEX idx_rule_device (device_id),
    CONSTRAINT fk_alert_rule_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则';

CREATE TABLE IF NOT EXISTS alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户（冗余，便于列表隔离）',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    device_key VARCHAR(64) NOT NULL COMMENT '设备标识快照',
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    rule_name VARCHAR(64) NOT NULL COMMENT '规则名快照',
    source_type VARCHAR(16) NOT NULL COMMENT '来源快照：THRESHOLD/OFFLINE/EVENT',
    severity VARCHAR(16) NOT NULL COMMENT '级别快照',
    identifier VARCHAR(64) NULL COMMENT '属性/事件标识符',
    title VARCHAR(255) NOT NULL COMMENT '告警标题',
    trigger_value VARCHAR(512) NULL COMMENT '触发值 / 事件输出（归一化文本或 JSON）',
    status VARCHAR(16) NOT NULL COMMENT 'TRIGGERED/ACKNOWLEDGED/RECOVERED',
    trigger_count INT NOT NULL DEFAULT 1 COMMENT '累计触发次数（抑制窗口内累加）',
    first_triggered_at DATETIME(3) NOT NULL COMMENT '首次触发时间',
    last_triggered_at DATETIME(3) NOT NULL COMMENT '最近触发时间（抑制窗口基准）',
    notified_at DATETIME(3) NULL COMMENT '最近一次回调通知时间',
    acknowledged_at DATETIME(3) NULL COMMENT '确认时间',
    acknowledged_by BIGINT NULL COMMENT '确认人用户ID',
    recovered_at DATETIME(3) NULL COMMENT '恢复时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    INDEX idx_alert_user_status (user_id, status),
    INDEX idx_alert_device_status (device_id, status),
    INDEX idx_alert_rule_open (rule_id, status, last_triggered_at),
    CONSTRAINT fk_alert_record_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警记录';
