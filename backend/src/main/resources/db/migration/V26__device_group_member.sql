-- T-48：分组级权限共享。
-- 成员权限为 VIEW / OPERATE；分组子树继承，成员管理仅限分组所有者。

CREATE TABLE IF NOT EXISTS device_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL COMMENT '共享分组ID',
    user_id BIGINT NOT NULL COMMENT '被共享用户ID',
    permission VARCHAR(16) NOT NULL COMMENT '权限：VIEW / OPERATE',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_group_member (group_id, user_id),
    INDEX idx_group_member_user (user_id, updated_at),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id)
        REFERENCES device_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组成员共享';
