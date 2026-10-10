-- ============================================================
-- V13：设备证书（T-25 认证方式扩展）
-- 用途：
--   1) device_certificate  设备证书生命周期（签发 + 吊销）
-- 说明：只加表，不改既有表，应用版本可安全回滚（回滚后新表留存，旧代码忽略新表）。
--       本期 X.509 走 EMQX mTLS 监听器（8883 + verify_peer + peer_cert_as_username=cn），
--       吊销不在 TLS 层执行 —— EMQX 5.0.26 的 SSL 监听器没有 crl_check / crl_file，
--       改由后端在认证回调内查询本表拒绝已吊销设备，与既有准入判定同源，吊销即时生效。
--       product.auth_mode 与 product.topic_prefix 由 T-11 建列，本期只改读写行为：
--       存量产品 auth_mode 均为 SECRET、topic_prefix 均为 device/{deviceKey}，
--       与既有硬编码前缀等价，迁移安全。
-- 注意：serial 唯一约束保证同一证书不被重复记录；device_id 建普通索引以支撑
--       「按设备查当前有效证书」的吊销热路径查询。
--       已吊销行保留不删（revoked_at 非空），便于审计追溯；同一设备重新签发写新行，
--       旧证书不自动吊销，由运维按需显式吊销。
--       本表不存私钥：设备私钥仅随签发响应返回一次，CA 私钥只从文件系统读取。
-- ============================================================

CREATE TABLE IF NOT EXISTS device_certificate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '所属设备（device.id）',
    product_key VARCHAR(64) NOT NULL COMMENT '产品键（冗余，供审计与模板比对）',
    device_key VARCHAR(64) NOT NULL COMMENT '设备键（冗余，供审计）',
    serial VARCHAR(64) NOT NULL COMMENT '证书序列号（十六进制，全局唯一）',
    subject VARCHAR(256) NOT NULL COMMENT '证书主体 DN，形如 CN={productKey}.{deviceKey},O=MQTT Cloud',
    issued_at DATETIME(3) NOT NULL COMMENT '签发时间（notBefore）',
    not_after DATETIME(3) NOT NULL COMMENT '失效时间（notAfter）',
    revoked_at DATETIME(3) NULL COMMENT '吊销时间，NULL=未吊销',
    revoke_reason VARCHAR(64) NULL COMMENT '吊销原因（不超过 64 字符）',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    CONSTRAINT uk_device_cert_serial UNIQUE (serial),
    INDEX idx_device_cert_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备证书（T-25）';
