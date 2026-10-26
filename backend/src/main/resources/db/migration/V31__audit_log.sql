-- T-53：平台审计日志。
-- 记录管理端 / 开放 API 的写操作（POST / PUT / PATCH / DELETE），用于合规审计与故障回溯。
-- 只追加不修改；user_id 刻意不加外键，用户删除后仍保留历史操作痕迹。

CREATE TABLE IF NOT EXISTS audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id       BIGINT       NULL COMMENT '操作者用户ID；匿名操作（激活等）为 NULL',
    username      VARCHAR(64)  NULL COMMENT '操作者用户名快照',
    role          VARCHAR(32)  NULL COMMENT '操作者角色快照',
    source        VARCHAR(16)  NOT NULL COMMENT '来源：CONSOLE / OPEN_API / DEVICE / ANONYMOUS',
    http_method   VARCHAR(8)   NOT NULL COMMENT 'HTTP 方法',
    path          VARCHAR(512) NOT NULL COMMENT '请求路径（不含 context-path）',
    action        VARCHAR(32)  NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/COMMAND/...',
    resource_type VARCHAR(32)  NOT NULL COMMENT '资源类型：DEVICE/PRODUCT/RULE/...',
    resource_id   VARCHAR(128) NULL COMMENT '资源标识（路径中的设备ID / deviceKey / 批次号等）',
    status_code   INT          NOT NULL COMMENT 'HTTP 响应状态码',
    success       TINYINT(1)   NOT NULL COMMENT '是否成功：1=成功，0=失败',
    error_message VARCHAR(512) NULL COMMENT '失败原因摘要（截断，不含请求体）',
    ip            VARCHAR(64)  NULL COMMENT '客户端 IP（优先 X-Forwarded-For / X-Real-IP）',
    user_agent    VARCHAR(255) NULL COMMENT '客户端 User-Agent',
    duration_ms   BIGINT       NOT NULL COMMENT '处理耗时（毫秒）',
    created_at    DATETIME(3)  NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_audit_user_time (user_id, created_at),
    KEY idx_audit_time (created_at),
    KEY idx_audit_action_time (action, created_at),
    KEY idx_audit_resource (resource_type, resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台审计日志';