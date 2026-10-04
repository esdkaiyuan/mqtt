-- ============================================================
-- V12：自动化 / 场景联动（T-23）
-- 用途：
--   1) scene_definition  场景定义（触发源 + 条件组 + 冷却）
--   2) scene_step        场景动作流步骤（有序 + 延时 + 目标 + 动作配置）
--   3) scene_execution   场景执行记录（触发快照 + 进度 + 状态）
--   4) scene_step_run    步骤执行明细（状态 + 尝试次数 + 重试锚点 + 载荷快照）
-- 说明：只加表，不改既有表，应用版本可安全回滚（回滚后新旧表共存，旧代码忽略新表）。
--       场景引擎完全运行在服务端：触发评估复用 T-14 已归一化的上行样本
--       （ThingModelInterpretServiceImpl 内第四条旁路，不新增 IngestDispatcher fan-out 路），
--       步骤出口复用既有能力「更新云端属性（device_property_latest）/ 下发命令（T-15 命令链路）/
--        转发外部 MQTT（T-19 RuleMqttForwarder，独立出站客户端，不经自有 EMQX）/
--        转发 HTTP（T-19 RuleHttpForwarder）」。
--       延时与重试锚点全部落库，由 SceneSweeper 巡检拉取，支持进程重启恢复与多副本安全。
--       不新增任何自有 MQTT 主题、不改 ACL。
--       逻辑删除下外键 ON DELETE CASCADE 不会触发（场景 / 设备均为逻辑删除，不物理删行），
--       仅作为运维物理清理时的完整性兜底。
-- 注意：scene_step_run.step_id 刻意不建外键 —— 场景更新采用「整体替换步骤」（先删旧 scene_step
--       再插新行），若建 ON DELETE CASCADE 外键，每次编辑场景都会连带删除历史执行明细。
--       step_id 仅作快照引用，scene_id / seq / action_type / delay_seconds 冗余成快照列。
-- ============================================================

CREATE TABLE IF NOT EXISTS scene_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户',
    name VARCHAR(64) NOT NULL COMMENT '场景名称',
    description VARCHAR(255) NULL COMMENT '描述',
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发源：PROPERTY/EVENT/TIMER',
    trigger_device_id BIGINT NULL COMMENT '触发限定设备ID，NULL=该用户全部设备（TIMER 源为空）',
    trigger_identifier VARCHAR(64) NULL COMMENT '触发属性/事件标识符（PROPERTY/EVENT 必填）',
    trigger_operator VARCHAR(8) NULL COMMENT '触发比较符：GT/GTE/LT/LTE/EQ/NE（空=任意上报即触发）',
    trigger_threshold VARCHAR(255) NULL COMMENT '触发比较阈值（文本，按物模型类型解释）',
    trigger_event_type VARCHAR(16) NULL COMMENT '事件类型过滤（EVENT 源可空）',
    timer_cron VARCHAR(64) NULL COMMENT '定时触发 cron（5 字段，分钟级；TIMER 源必填）',
    condition_logic VARCHAR(8) NULL COMMENT '条件组合：AND/OR（缺省 AND；条件组为空表示无附加条件）',
    condition_config TEXT NULL COMMENT '条件组 JSON 数组（每项：deviceId?/identifier/operator/threshold）',
    cooldown_seconds INT NOT NULL DEFAULT 0 COMMENT '触发冷却窗口（秒），0=不限制',
    last_triggered_at DATETIME(3) NULL COMMENT '最近一次触发时间（展示 + 定时触发分钟去重锚点）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '启用：1/0',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：1/0',
    INDEX idx_scene_user (user_id, enabled, deleted),
    INDEX idx_scene_trigger (user_id, trigger_type, trigger_identifier, enabled),
    INDEX idx_scene_device (trigger_device_id),
    INDEX idx_scene_timer (trigger_type, enabled, deleted),
    CONSTRAINT fk_scene_definition_device FOREIGN KEY (trigger_device_id)
        REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景定义';

CREATE TABLE IF NOT EXISTS scene_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene_id BIGINT NOT NULL COMMENT '场景ID',
    seq INT NOT NULL COMMENT '步骤序号，从 1 开始',
    delay_seconds INT NOT NULL DEFAULT 0 COMMENT '本步骤执行前延时（秒），相对前一步完成时刻',
    action_type VARCHAR(32) NOT NULL COMMENT 'UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP',
    target_type VARCHAR(16) NOT NULL DEFAULT 'TRIGGER' COMMENT '目标：TRIGGER=触发设备 / FIXED=固定目标集合',
    target_config TEXT NULL COMMENT '固定目标 JSON（BatchTargetRequest 形状：deviceIds/productIds/groupIds/tagIds）',
    action_config TEXT NOT NULL COMMENT '动作配置 JSON（形状同 T-19 §5.2）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '启用：1/0',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_scene_step_seq (scene_id, seq),
    CONSTRAINT fk_scene_step_scene FOREIGN KEY (scene_id)
        REFERENCES scene_definition(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景动作流步骤';

CREATE TABLE IF NOT EXISTS scene_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户（冗余，便于按用户查询）',
    scene_id BIGINT NOT NULL COMMENT '场景ID',
    scene_name VARCHAR(64) NOT NULL COMMENT '场景名称快照',
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发源快照',
    trigger_device_id BIGINT NULL COMMENT '触发设备ID（TIMER 源为空）',
    trigger_device_key VARCHAR(64) NULL COMMENT '触发设备标识快照',
    trigger_device_name VARCHAR(128) NULL COMMENT '触发设备名称快照',
    trigger_identifier VARCHAR(64) NULL COMMENT '触发标识符快照',
    trigger_value VARCHAR(512) NULL COMMENT '触发值文本快照',
    trigger_event_type VARCHAR(16) NULL COMMENT '触发事件类型快照',
    trigger_source VARCHAR(16) NOT NULL DEFAULT 'AUTO' COMMENT '触发方式：AUTO=自动 / MANUAL=手动执行',
    total_steps INT NOT NULL DEFAULT 0 COMMENT '启用步骤总数快照',
    finished_steps INT NOT NULL DEFAULT 0 COMMENT '已完成（SUCCESS/SKIPPED）步骤数',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    error_message VARCHAR(512) NULL COMMENT '最近一次失败原因',
    started_at DATETIME(3) NULL COMMENT '首个步骤开始执行时间',
    finished_at DATETIME(3) NULL COMMENT '终态时间（SUCCESS/FAILED）',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    INDEX idx_scene_exec_user_status (user_id, status, created_at),
    INDEX idx_scene_exec_scene (scene_id, created_at),
    INDEX idx_scene_exec_device (trigger_device_id, created_at),
    CONSTRAINT fk_scene_execution_scene FOREIGN KEY (scene_id)
        REFERENCES scene_definition(id) ON DELETE CASCADE,
    CONSTRAINT fk_scene_execution_device FOREIGN KEY (trigger_device_id)
        REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景执行记录';

CREATE TABLE IF NOT EXISTS scene_step_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id BIGINT NOT NULL COMMENT '执行记录ID',
    scene_id BIGINT NOT NULL COMMENT '场景ID（冗余，便于按场景排查）',
    step_id BIGINT NOT NULL COMMENT '步骤ID（仅作快照引用，无外键：场景更新会整体替换步骤）',
    seq INT NOT NULL COMMENT '步骤序号快照',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型快照',
    delay_seconds INT NOT NULL DEFAULT 0 COMMENT '延时快照（秒）',
    scheduled_at DATETIME(3) NULL COMMENT '计划执行时刻（前一步完成 + delay）；NULL=尚未排期',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    next_attempt_at DATETIME(3) NULL COMMENT '下次可执行时间；NULL=尚未排期（非终态且不参与巡检）',
    error_message VARCHAR(512) NULL COMMENT '最近一次失败原因',
    forward_payload TEXT NULL COMMENT '转发载荷快照（HTTP/MQTT 步骤，便于排障）',
    started_at DATETIME(3) NULL COMMENT '本步开始执行时间',
    finished_at DATETIME(3) NULL COMMENT '本步终态时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    INDEX idx_scene_step_run_exec (execution_id, seq),
    INDEX idx_scene_step_run_due (status, next_attempt_at),
    CONSTRAINT fk_scene_step_run_execution FOREIGN KEY (execution_id)
        REFERENCES scene_execution(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景步骤执行明细';