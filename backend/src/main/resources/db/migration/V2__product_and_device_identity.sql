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

-- 2. 默认产品（存量设备归入）
INSERT INTO product (product_key, product_name, description, auth_mode, topic_prefix, payload_format, status)
VALUES ('legacy', '默认产品（存量设备）', 'T-11 迁移前已存在设备的归属产品', 'SECRET', 'device/{deviceKey}', 'JSON', 'ENABLED')
ON DUPLICATE KEY UPDATE product_key = product_key;

-- 3. device 增加 product_id（先可空 → 回填 → 置非空）
ALTER TABLE device ADD COLUMN product_id BIGINT NULL COMMENT '所属产品ID';
UPDATE device SET product_id = (SELECT id FROM product WHERE product_key = 'legacy') WHERE product_id IS NULL;
ALTER TABLE device MODIFY COLUMN product_id BIGINT NOT NULL COMMENT '所属产品ID';
ALTER TABLE device ADD CONSTRAINT fk_device_product FOREIGN KEY (product_id) REFERENCES product(id);

-- 4. 唯一键由 device_key 全局唯一收敛为 (product_id, device_key)
ALTER TABLE device DROP INDEX device_key;
ALTER TABLE device ADD UNIQUE KEY uk_product_device (product_id, device_key);

-- 5. 一机一密凭据与连接许可
ALTER TABLE device ADD COLUMN device_secret_hash VARCHAR(100) NULL COMMENT '一机一密密钥哈希(BCrypt)';
ALTER TABLE device ADD COLUMN secret_updated_at DATETIME NULL COMMENT '密钥重置时间';
ALTER TABLE device ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 COMMENT '连接许可：1=允许，0=禁止';