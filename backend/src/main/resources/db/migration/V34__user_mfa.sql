-- T-58：用户 TOTP 多因素认证（MFA）。
-- mfa_secret 存 AES-GCM 密文（Base64），绝不明文落库；mfa_enabled=0 但 secret 非空表示「已发起绑定未确认」。

ALTER TABLE sys_user
    ADD COLUMN mfa_enabled TINYINT NOT NULL DEFAULT 0
        COMMENT '是否启用 TOTP MFA（T-58）' AFTER status,
    ADD COLUMN mfa_secret TEXT NULL
        COMMENT 'TOTP 密钥密文（AES-GCM，Base64）；NULL=未绑定' AFTER mfa_enabled,
    ADD COLUMN mfa_confirmed_at DATETIME NULL
        COMMENT 'MFA 启用时间' AFTER mfa_secret;