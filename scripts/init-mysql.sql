-- ============================================================
-- MQTT云平台系统 - MySQL数据库初始化脚本
-- 适用版本：MySQL 8.0+
-- ============================================================

-- 1. 创建数据库
-- 注意：MySQL 的 CREATE DATABASE 不支持 COMMENT 子句（MariaDB 才支持），
-- 这里只保留字符集与排序规则，避免初始化在 MySQL 8.x 上直接语法报错。
CREATE DATABASE IF NOT EXISTS mqtt_cloud
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE mqtt_cloud;

-- 2. 创建用户认证表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) UNIQUE COMMENT '手机号',
    role ENUM('ADMIN', 'OPERATOR', 'VIEWER') DEFAULT 'VIEWER' COMMENT '角色',
    status ENUM('ACTIVE', 'DISABLED', 'LOCKED') DEFAULT 'ACTIVE' COMMENT '账号状态',
    last_login DATETIME COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '逻辑删除标记：0=未删除，1=已删除',
    INDEX idx_username (username),
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_last_login (last_login),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 3. 创建设备表
CREATE TABLE IF NOT EXISTS device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_key VARCHAR(100) NOT NULL UNIQUE COMMENT '设备唯一标识',
    device_type VARCHAR(50) NOT NULL COMMENT '设备类型',
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT Topic',
    description TEXT COMMENT '设备描述',
    owner_id BIGINT NOT NULL COMMENT '所属用户ID',
    status ENUM('ONLINE', 'OFFLINE', 'INACTIVE') DEFAULT 'INACTIVE' COMMENT '设备状态',
    last_seen DATETIME COMMENT '最后上报时间',
    metadata JSON COMMENT '设备扩展元数据',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '逻辑删除标记：0=未删除，1=已删除',
    INDEX idx_device_key (device_key),
    INDEX idx_owner (owner_id),
    INDEX idx_status (status),
    INDEX idx_last_seen (last_seen),
    INDEX idx_device_type (device_type),
    INDEX idx_deleted (deleted),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

-- 4. 创建设备状态历史表
CREATE TABLE IF NOT EXISTS device_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    status ENUM('ONLINE', 'OFFLINE') NOT NULL COMMENT '状态',
    timestamp DATETIME NOT NULL COMMENT '状态变更时间',
    INDEX idx_device_id (device_id),
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_timestamp (timestamp),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备状态历史表';

-- 5. 创建消息表
CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT Topic',
    direction ENUM('PUBLISH', 'SUBSCRIBE') NOT NULL COMMENT '消息方向',
    payload TEXT COMMENT '消息载荷',
    qos INT DEFAULT 0 COMMENT 'QoS等级',
    device_id BIGINT COMMENT '关联设备ID',
    sent_at DATETIME NOT NULL COMMENT '消息发送时间',
    received_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '消息接收时间',
    INDEX idx_topic (topic),
    INDEX idx_device (device_id),
    INDEX idx_sent_at (sent_at),
    INDEX idx_direction (direction),
    INDEX idx_device_sent (device_id, sent_at),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- 6. 创建历史记录表
CREATE TABLE IF NOT EXISTS history_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    topic VARCHAR(255) NOT NULL COMMENT 'Topic',
    payload JSON COMMENT '数据载荷',
    timestamp DATETIME NOT NULL COMMENT '记录时间',
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_timestamp (timestamp),
    INDEX idx_device_topic (device_id, topic),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史记录表';

-- 7. 创建API密钥表
CREATE TABLE IF NOT EXISTS api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    name VARCHAR(100) NOT NULL COMMENT 'API Key名称',
    key_value VARCHAR(64) NOT NULL UNIQUE COMMENT 'API Key值（64位十六进制）',
    permissions JSON COMMENT '权限范围，如 ["device:read","device:write","data:read"]',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    last_used_at DATETIME COMMENT '最后使用时间',
    expires_at DATETIME COMMENT '过期时间，NULL为永不过期',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_key_value (key_value),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API密钥表';

-- 8. 创建Webhook配置表
CREATE TABLE IF NOT EXISTS webhook_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    device_id BIGINT COMMENT '设备ID，NULL表示监听所有设备',
    name VARCHAR(100) NOT NULL COMMENT 'Webhook名称',
    url VARCHAR(500) NOT NULL COMMENT '回调URL',
    secret VARCHAR(255) COMMENT '签名密钥（HMAC-SHA256）',
    events JSON COMMENT '事件类型列表，如 ["device.data","device.heartbeat"]',
    headers JSON COMMENT '自定义HTTP请求头',
    retry_count INT DEFAULT 3 COMMENT '失败重试次数',
    timeout_seconds INT DEFAULT 10 COMMENT '请求超时秒数',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    last_triggered_at DATETIME COMMENT '最后触发时间',
    failure_count INT DEFAULT 0 COMMENT '连续失败次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_device (user_id, device_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook回调配置表';

-- 9. 插入默认管理员账号
-- 用户名：admin，密码：admin123（BCrypt加密后的值）
INSERT INTO sys_user (username, password, email, role, status)
VALUES (
    'admin',
    '$2a$10$GbLOksTglzegx2KgJiLL4eXZg8nAMlAySBgU/OyyW9vgsTM/TTXd6',
    'admin@mqtt-cloud.local',
    'ADMIN',
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE username = username;

-- 10. 插入测试数据
INSERT IGNORE INTO sys_user (username, password, email, role, status)
VALUES
    ('operator1', '$2a$10$GbLOksTglzegx2KgJiLL4eXZg8nAMlAySBgU/OyyW9vgsTM/TTXd6', 'op1@test.local', 'OPERATOR', 'ACTIVE'),
    ('viewer1', '$2a$10$GbLOksTglzegx2KgJiLL4eXZg8nAMlAySBgU/OyyW9vgsTM/TTXd6', 'viewer1@test.local', 'VIEWER', 'ACTIVE');

INSERT IGNORE INTO device (device_name, device_key, device_type, topic, description, owner_id, status, metadata)
VALUES
    ('温度传感器-01', 'sensor-temp-001', 'sensor', 'device/sensor-temp-001/data', '客厅温度传感器', 1, 'INACTIVE', '{"unit":"°C","min":-20,"max":80}'),
    ('湿度传感器-01', 'sensor-hum-001', 'sensor', 'device/sensor-hum-001/data', '客厅湿度传感器', 1, 'INACTIVE', '{"unit":"%","min":0,"max":100}'),
    ('智能开关-01', 'switch-001', 'actuator', 'device/switch-001/control', '客厅主灯开关', 1, 'INACTIVE', '{"type":"relay","channels":1}');

-- 完成提示
SELECT 'MQTT云平台数据库初始化完成！' AS message;
SELECT COUNT(*) AS user_count FROM sys_user;
SELECT COUNT(*) AS device_count FROM device;
