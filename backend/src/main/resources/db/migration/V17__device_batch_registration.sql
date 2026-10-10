-- T-30 · 批量注册与设备激活
-- 目标：对标阿里云/腾讯云「批量注册实例 → 激活 → 烧录凭据」标准流程，
-- 补齐当前仅有单设备创建（POST /devices）的接入断层。

-- 1) 注册批次：一次批量创建落一个批次，便于追溯「谁在何时导入了哪些设备」
CREATE TABLE device_reg_batch (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_no        VARCHAR(64)  NOT NULL COMMENT '批次号（对外唯一标识）',
    product_id      BIGINT       NOT NULL COMMENT '所属产品ID',
    owner_id        BIGINT       NOT NULL COMMENT '归属用户ID',
    name_pattern    VARCHAR(128) NOT NULL COMMENT 'deviceKey 生成模板，如 sensor-{index}',
    prefix          VARCHAR(64)  NOT NULL COMMENT 'deviceKey 前缀',
    start_index     INT          NOT NULL DEFAULT 1 COMMENT '起始序号',
    total_count     INT          NOT NULL COMMENT '计划创建设备数',
    succeeded_count INT          NOT NULL DEFAULT 0 COMMENT '成功创建数',
    failed_count    INT          NOT NULL DEFAULT 0 COMMENT '失败数',
    -- 失败明细 JSON 数组：[{index, deviceKey, reason}]
    failed_items    TEXT         NULL COMMENT '失败明细（JSON）',
    -- 明文凭据 JSON 数组：[{deviceKey, username, secret}]，仅创建时一次性返回
    credentials     MEDIUMTEXT   NULL COMMENT '一次性凭据明文（JSON）',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    KEY idx_owner_created (owner_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备注册批次';

-- 2) device 表扩展：
--    - activate_code：设备激活码（烧录/首次连接时消费），NULL 表示未走激活流程
--    - activated_at：激活时间，NULL 表示未激活
--    - batch_id：来源注册批次，便于反查批量导入的设备
ALTER TABLE device
    ADD COLUMN activate_code VARCHAR(64) NULL COMMENT '设备激活码，NULL=未走激活流程' AFTER parent_id,
    ADD COLUMN activated_at  DATETIME(3) NULL COMMENT '激活时间，NULL=未激活' AFTER activate_code,
    ADD COLUMN batch_id      BIGINT     NULL COMMENT '来源注册批次ID' AFTER activated_at,
    ADD UNIQUE KEY uk_activate_code (activate_code),
    ADD KEY idx_batch (batch_id);
