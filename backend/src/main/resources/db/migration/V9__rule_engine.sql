-- ============================================================
-- V9：消息规则 / 规则引擎（T-19）
-- 用途：
--   1) rule_definition  规则定义（触发源 + 条件 + 动作 + 冷却）
--   2) rule_execution   规则执行记录（快照 + 状态 + 尝试次数 + 重试锚点 + 载荷快照）
-- 说明：只加表，不改既有表，应用版本可安全回滚（回滚后新旧表共存，旧代码忽略新表）。
--       规则引擎完全运行在服务端：条件判定复用 T-14 已归一化的上行样本
--       （ThingModelInterpretServiceImpl 内旁路），动作出口为
--       「更新云端属性（复用 device_property_latest）/ 下发命令（复用 T-15 命令链路）/
--        转发外部 MQTT（独立出站 Paho 客户端，不经自有 EMQX）/ 转发 HTTP（独立出站 POST）」。
--       不新增任何自有 MQTT 主题、不改 ACL。
--       逻辑删除下外键 ON DELETE CASCADE 不会触发（规则 / 设备均为逻辑删除，不物理删行），
--       仅作为运维物理清理时的完整性兜底。
-- ============================================================

CREATE TABLE IF NOT EXISTS rule_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户',
    name VARCHAR(64) NOT NULL COMMENT '规则名称',
    description VARCHAR(255) NULL COMMENT '描述',
    device_id BIGINT NULL COMMENT '限定设备ID，NULL=该用户全部设备',
    source_type VARCHAR(16) NOT NULL COMMENT '触发源：PROPERTY/EVENT',
    identifier VARCHAR(64) NOT NULL COMMENT '属性标识符或事件标识符',
    operator VARCHAR(8) NULL COMMENT '比较符：GT/GTE/LT/LTE/EQ/NE（EVENT 源可空）',
    threshold_value VARCHAR(255) NULL COMMENT '比较阈值（文本，按物模型类型解释）',
    event_type VARCHAR(16) NULL COMMENT '事件类型过滤（EVENT 源可空）',
    action_type VARCHAR(32) NOT NULL COMMENT '动作：UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP',
    action_config TEXT NOT NULL COMMENT '动作配置 JSON（按 action_type 解释）',
    cooldown_seconds INT NOT NULL DEFAULT 0 COMMENT '触发冷却窗口（秒），0=不限制',
    last_triggered_at DATETIME(3) NULL COMMENT '最近一次触发时间（由异步执行路径回填，仅供展示；冷却判定用进程内状态）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '启用：1/0',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：1/0',
    INDEX idx_rule_user (user_id, enabled, deleted),
    INDEX idx_rule_match (user_id, source_type, identifier, enabled),
    INDEX idx_rule_device (device_id),
    CONSTRAINT fk_rule_definition_device FOREIGN KEY (device_id)
        REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息规则定义';

CREATE TABLE IF NOT EXISTS rule_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户（冗余，便于按用户查询）',
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    rule_name VARCHAR(64) NOT NULL COMMENT '规则名称快照',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    device_key VARCHAR(64) NOT NULL COMMENT '设备标识快照',
    device_name VARCHAR(128) NULL COMMENT '设备名称快照',
    source_type VARCHAR(16) NOT NULL COMMENT '触发源快照',
    identifier VARCHAR(64) NOT NULL COMMENT '触发标识符',
    trigger_value VARCHAR(512) NULL COMMENT '触发值文本',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型快照',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING/SUCCESS/FAILED',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    next_attempt_at DATETIME(3) NULL COMMENT '下次重试时间；NULL 表示无待重试（已投递待首次执行 / 已终态）',
    error_message VARCHAR(512) NULL COMMENT '最近一次失败原因',
    forward_payload TEXT NULL COMMENT '转发载荷快照（HTTP/MQTT 动作，便于排障）',
    finished_at DATETIME(3) NULL COMMENT '终态时间（SUCCESS/FAILED）',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    INDEX idx_exec_user_status (user_id, status, created_at),
    INDEX idx_exec_rule (rule_id, created_at),
    INDEX idx_exec_device (device_id, created_at),
    INDEX idx_exec_retry (status, next_attempt_at),
    CONSTRAINT fk_rule_execution_rule FOREIGN KEY (rule_id)
        REFERENCES rule_definition(id) ON DELETE CASCADE,
    CONSTRAINT fk_rule_execution_device FOREIGN KEY (device_id)
        REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则执行记录';