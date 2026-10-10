-- ============================================================
-- V16：规则与告警作用域扩展（T-28）
-- 用途：
--   1) alert_rule      追加 scope_type / group_ids / tag_ids 三列与作用域索引
--   2) rule_definition 追加 scope_type / group_ids / tag_ids 三列与作用域索引
-- 说明：只加列与索引，不改既有列，应用版本可安全回滚（回滚后新列留存，旧代码忽略）。
--       scope_type 四值：ALL（该用户全部设备，等价于旧 device_id IS NULL）/
--       DEVICE（单台设备，等价于旧 device_id 非空）/ GROUP（选中分组及其全部后代分组内的设备）/
--       TAG（选中标签关联的设备）。
--       group_ids / tag_ids 为 JSON 数组文本，沿用 rule_definition.action_config 与
--       webhook_config.events 的文本口径；只落 ID 不落名称快照，名称变更后展示随实体变化。
--       既有 device_id 列与外键保持不变，DEVICE 档语义与改造前的「单设备」逐位一致。
-- 注意：存量行回填为 ALL / DEVICE 后与旧语义等价，故无需回滚脚本；
--       分组档刻意不落库预展开（不存「自身 + 后代」的全集），改由匹配时按设备隶属上溯祖先，
--       使作用域与分组树变更解耦 —— 见 T-28 设计文档 §4.3。
-- ============================================================

ALTER TABLE alert_rule ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '作用域档位：ALL / DEVICE / GROUP / TAG';
ALTER TABLE alert_rule ADD COLUMN group_ids TEXT NULL COMMENT 'GROUP 档分组ID JSON 数组，其余档为 NULL';
ALTER TABLE alert_rule ADD COLUMN tag_ids TEXT NULL COMMENT 'TAG 档标签ID JSON 数组，其余档为 NULL';
ALTER TABLE alert_rule ADD INDEX idx_alert_rule_scope (user_id, scope_type);

ALTER TABLE rule_definition ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '作用域档位：ALL / DEVICE / GROUP / TAG';
ALTER TABLE rule_definition ADD COLUMN group_ids TEXT NULL COMMENT 'GROUP 档分组ID JSON 数组，其余档为 NULL';
ALTER TABLE rule_definition ADD COLUMN tag_ids TEXT NULL COMMENT 'TAG 档标签ID JSON 数组，其余档为 NULL';
ALTER TABLE rule_definition ADD INDEX idx_rule_scope (user_id, scope_type);

-- 存量行回填：device_id 非空即单设备档，为空即全部设备档，与旧语义逐位等价。
-- 新增列的默认值已覆盖此后新插入的行。
UPDATE alert_rule SET scope_type = CASE WHEN device_id IS NULL THEN 'ALL' ELSE 'DEVICE' END;
UPDATE rule_definition SET scope_type = CASE WHEN device_id IS NULL THEN 'ALL' ELSE 'DEVICE' END;