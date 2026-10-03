-- ============================================================
-- V10：属性时序与可视化（T-21）
-- 用途：
--   1) device_property_history  设备属性历史（追加写，时序数据）
--   2) dashboard                可保存看板（面板配置为 JSON）
-- 说明：只加表，不改既有表，应用版本可安全回滚（回滚后新旧表共存，旧代码忽略新表）。
--       属性历史由 ThingModelInterpretServiceImpl 内的第 4 条旁路追加写（异常只记 WARN，
--       不上抛、不中断本批、不进死信），保留策略由巡检按 reported_at 分批硬删除。
--       看板仅归属创建者本人，删除为硬删除。
--       两张表均无逻辑删除列（不受 logic-delete-field: deleted 影响）。
--       不新增任何自有 MQTT 主题、不改 ACL。
-- ============================================================

CREATE TABLE IF NOT EXISTS device_property_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    identifier VARCHAR(64) NOT NULL COMMENT '属性标识符',
    data_type VARCHAR(16) NOT NULL COMMENT '数据类型（落库时的物模型定义）',
    value_text TEXT COMMENT '属性值（统一文本存储，按 data_type 解释）',
    reported_at DATETIME(3) NOT NULL COMMENT '设备上报时间（毫秒精度）',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入库时间',
    INDEX idx_device_identifier_time (device_id, identifier, reported_at),
    INDEX idx_reported_at (reported_at),
    CONSTRAINT fk_prop_history_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备属性历史（追加写，时序数据）';

CREATE TABLE IF NOT EXISTS dashboard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '创建者（sys_user.id）',
    name VARCHAR(64) NOT NULL COMMENT '看板名称',
    config JSON NOT NULL COMMENT '看板配置（面板数组）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    CONSTRAINT fk_dashboard_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可保存看板';
