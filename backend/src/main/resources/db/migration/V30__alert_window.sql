-- T-52：告警阈值窗口聚合。
-- window_seconds=0 保持既有「最新值」语义；窗口模式仅对 THRESHOLD 生效。

ALTER TABLE alert_rule
    ADD COLUMN aggregate_type VARCHAR(16) NULL COMMENT '窗口聚合方式：AVG/MIN/MAX/SUM/COUNT；实时模式为 NULL' AFTER threshold_value,
    ADD COLUMN window_seconds INT NOT NULL DEFAULT 0 COMMENT '统计窗口（秒），0=实时最新值，1~86400' AFTER aggregate_type,
    ADD COLUMN min_samples INT NOT NULL DEFAULT 1 COMMENT '窗口内最少样本数，达到后才评估（1~1000）' AFTER window_seconds;
