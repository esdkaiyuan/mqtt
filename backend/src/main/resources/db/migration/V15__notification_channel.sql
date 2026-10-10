-- ============================================================
-- V15：通知渠道扩展（T-27）
-- 用途：
--   1) notification               站内信（内置必达通道）
--   2) notification_channel       外部渠道配置（EMAIL / SMS）
--   3) notification_delivery      投递记录（含重试锚点）
--   4) alert_escalation_policy    告警升级策略
--   5) alert_record 追加 assignee_id / escalation_level / last_escalated_at 三列
-- 说明：只加表与列，不改既有列，应用版本可安全回滚（回滚后新表新列留存，旧代码忽略）。
--       既有 webhook_config 表与告警评估链路零改动：Webhook 出口经适配器纳入投递记录，
--       其配置表结构与投递语义保持原样。
--       存量 alert_record 行三列回退为「无处理人、未升级」，与既有状态机语义等价。
-- 注意：notification_delivery.channel_id 用 ON DELETE SET NULL 而非级联删除 ——
--       渠道删除后投递留痕仍需保留供审计，channel_type 已冗余在行上，语义不失真。
--       alert_record.assignee_id 刻意不加外键：用户被删除后保留历史指派痕迹，
--       与既有 acknowledged_by 同口径。
--       notification_delivery 不落完整请求体，只落错误摘要，避免凭据写入日志表。
-- ============================================================

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户ID',
    event_type VARCHAR(64) NOT NULL COMMENT '来源事件名，如 alert.triggered',
    level VARCHAR(16) NOT NULL COMMENT '展示级别：INFO / WARNING / CRITICAL',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT COMMENT '正文',
    ref_type VARCHAR(32) COMMENT '关联对象类型，本期为 ALERT',
    ref_id BIGINT COMMENT '关联对象主键',
    read_at DATETIME COMMENT '已读时间，NULL 表示未读',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_read (user_id, read_at, created_at),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内信表（T-27）';

CREATE TABLE IF NOT EXISTS notification_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户ID',
    channel_type VARCHAR(16) NOT NULL COMMENT '渠道类型：EMAIL / SMS',
    name VARCHAR(100) NOT NULL COMMENT '渠道名称',
    config JSON NOT NULL COMMENT '按类型不同的配置对象',
    events JSON COMMENT '订阅的事件名数组',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    last_triggered_at DATETIME COMMENT '最近成功投递时间',
    failure_count INT DEFAULT 0 COMMENT '连续失败次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_channel_name (user_id, name),
    INDEX idx_user_type (user_id, channel_type),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知渠道配置表（T-27）';

CREATE TABLE IF NOT EXISTS notification_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户ID',
    channel_id BIGINT COMMENT '渠道配置ID；内置通道为 NULL',
    channel_type VARCHAR(16) NOT NULL COMMENT '渠道类型：INAPP / EMAIL / SMS / WEBHOOK',
    event_type VARCHAR(64) NOT NULL COMMENT '事件名',
    target VARCHAR(255) COMMENT '投递目标摘要（邮箱、脱敏手机号、URL 主机）',
    title VARCHAR(200) COMMENT '标题快照',
    status VARCHAR(16) NOT NULL COMMENT '投递状态：SUCCESS / FAILED',
    attempt_count INT DEFAULT 1 COMMENT '已尝试次数',
    next_attempt_at DATETIME COMMENT '下次重试时间；NULL 表示不再重试',
    error_message VARCHAR(500) COMMENT '失败摘要',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status_next (status, next_attempt_at),
    INDEX idx_user_created (user_id, created_at),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (channel_id) REFERENCES notification_channel(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知投递记录表（T-27）';

CREATE TABLE IF NOT EXISTS alert_escalation_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户ID',
    name VARCHAR(100) NOT NULL COMMENT '策略名称',
    severity VARCHAR(16) NOT NULL COMMENT '适用级别：ALL / INFO / WARNING / CRITICAL',
    after_seconds INT NOT NULL COMMENT '未确认时长阈值（秒）',
    assignee_id BIGINT COMMENT '升级时指派的处理人用户ID，可为空',
    channel_ids JSON COMMENT '升级时额外通知的渠道ID数组，可为空',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '逻辑删除标记：0=未删除，1=已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_enabled (user_id, enabled, deleted),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警升级策略表（T-27）';

ALTER TABLE alert_record ADD COLUMN assignee_id BIGINT NULL COMMENT '处理人用户ID，NULL 表示未指派';
ALTER TABLE alert_record ADD COLUMN escalation_level INT NOT NULL DEFAULT 0 COMMENT '已升级次数，0 表示未升级';
ALTER TABLE alert_record ADD COLUMN last_escalated_at DATETIME NULL COMMENT '最近升级时间，作为下一级升级的基准';