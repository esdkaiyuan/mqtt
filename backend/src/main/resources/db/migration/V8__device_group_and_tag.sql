-- ============================================================
-- V8：设备分组与标签（T-18）
-- 用途：
--   1) device_group          设备分组树（自引用 parent_id）
--   2) device_tag            设备标签（扁平，含展示色）
--   3) device_group_relation 设备-分组多对多关联
--   4) device_tag_relation   设备-标签多对多关联
-- 说明：只加表，不改既有表，应用版本可安全回滚（回滚后新旧表共存，旧代码忽略新表）。
--       分组与标签是纯服务端组织结构，不下发到设备、不新增 MQTT 主题、不改 ACL；
--       批量下发命令复用 T-15 的 device/{deviceKey}/cmd/down 链路。
--       关联表为**物理行**（无 deleted 列）：解除关联即物理删除，避免逻辑删除与唯一键冲突。
--       自引用外键 fk_device_group_parent 在逻辑删除下不会触发 SET NULL（逻辑删除不物理删行），
--       仅作为运维物理清理时的完整性兜底。
--       递归后代查询使用 MySQL 8 递归 CTE（见 DeviceGroupMapper.selectDescendantIds）。
-- ============================================================

CREATE TABLE IF NOT EXISTS device_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户',
    parent_id BIGINT NULL COMMENT '父分组ID，NULL=根分组',
    name VARCHAR(64) NOT NULL COMMENT '分组名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同级排序（升序）',
    description VARCHAR(255) NULL COMMENT '描述',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：1/0',
    INDEX idx_group_user_parent (user_id, parent_id, deleted),
    CONSTRAINT fk_device_group_parent FOREIGN KEY (parent_id)
        REFERENCES device_group(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组树';

CREATE TABLE IF NOT EXISTS device_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '归属用户',
    name VARCHAR(64) NOT NULL COMMENT '标签名称',
    color VARCHAR(16) NULL COMMENT '展示色（如 #409EFF）',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：1/0',
    INDEX idx_tag_user (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备标签';

CREATE TABLE IF NOT EXISTS device_group_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    group_id BIGINT NOT NULL COMMENT '分组ID',
    created_at DATETIME(3) NOT NULL COMMENT '关联时间',
    UNIQUE KEY uk_device_group (device_id, group_id),
    INDEX idx_group_rel_group (group_id),
    CONSTRAINT fk_group_rel_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_rel_group FOREIGN KEY (group_id) REFERENCES device_group(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备-分组关联';

CREATE TABLE IF NOT EXISTS device_tag_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    tag_id BIGINT NOT NULL COMMENT '标签ID',
    created_at DATETIME(3) NOT NULL COMMENT '关联时间',
    UNIQUE KEY uk_device_tag (device_id, tag_id),
    INDEX idx_tag_rel_tag (tag_id),
    CONSTRAINT fk_tag_rel_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE,
    CONSTRAINT fk_tag_rel_tag FOREIGN KEY (tag_id) REFERENCES device_tag(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备-标签关联';