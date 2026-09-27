-- ============================================================
-- V2 设备接入与身份体系：产品表 + 设备表改造
-- ============================================================

-- 1. 产品表（设备类型模板）
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_key VARCHAR(64) NOT NULL UNIQUE COMMENT '产品唯一标识，用于拼 Topic 与用户名',
    product_name VARCHAR(128) NOT NULL COMMENT '产品名称',
    description VARCHAR(512) COMMENT '描述',
    auth_mode VARCHAR(32) NOT NULL DEFAULT 'SECRET' COMMENT '认证方式：SECRET/X509/JWT',
    topic_prefix VARCHAR(128) NOT NULL DEFAULT 'device/{deviceKey}' COMMENT 'Topic 命名模板',
    payload_format VARCHAR(16) NOT NULL DEFAULT 'JSON' COMMENT '载荷格式：JSON/RAW',
    metadata_schema TEXT COMMENT '可选 JSON Schema',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '逻辑删除标记',
    INDEX idx_product_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品表';