-- T-54：设备当前固件版本。
-- 由设备 OTA 成功回传（device/{key}/ota, status=success）回写；NULL 表示平台尚未获知版本。
-- 用于 OTA 建任务时的降级拦截，以及设备详情 / 开放 API 展示。

ALTER TABLE device
    ADD COLUMN firmware_version VARCHAR(64) NULL
        COMMENT '当前固件版本（OTA 成功后回写；NULL=未知）' AFTER parent_id;

-- 任务详情持久化降级拦截台数（建任务时计算一次，后续详情 / 外部 API 可直接读取）
ALTER TABLE ota_upgrade_task
    ADD COLUMN downgrade_skipped_count INT NOT NULL DEFAULT 0
        COMMENT '建任务时因当前版本高于目标版本而被跳过的设备数（T-54）' AFTER skipped_count;