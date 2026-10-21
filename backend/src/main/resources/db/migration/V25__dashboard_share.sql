-- T-46：看板大屏与跨用户分享。
-- 只加表：令牌只存 SHA-256 哈希与短前缀，明文仅在创建 / 轮换时返回一次。

CREATE TABLE IF NOT EXISTS dashboard_share (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '创建者（sys_user.id）',
    name VARCHAR(64) NOT NULL COMMENT '分享名称（大屏标题）',
    token_hash CHAR(64) NOT NULL COMMENT '分享令牌 SHA-256 十六进制（不存明文）',
    token_preview VARCHAR(12) NOT NULL COMMENT '令牌前缀，仅用于列表识别',
    dashboard_ids TEXT NOT NULL COMMENT '看板 ID JSON 数组（按轮播顺序）',
    interval_seconds INT NOT NULL DEFAULT 15 COMMENT '轮播间隔（秒）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '启用：1/0',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_dashboard_share_token (token_hash),
    INDEX idx_dashboard_share_user (user_id, updated_at),
    CONSTRAINT fk_dashboard_share_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板分享';
