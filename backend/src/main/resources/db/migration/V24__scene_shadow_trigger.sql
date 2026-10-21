-- T-45：场景影子变更触发。
-- trigger_type 列宽已足够，仅同步注释；新增影子变更侧配置（仅 SHADOW 源使用）。

ALTER TABLE scene_definition
    MODIFY COLUMN trigger_type VARCHAR(16) NOT NULL
        COMMENT '触发源：PROPERTY/EVENT/TIMER/ONLINE/OFFLINE/SHADOW',
    ADD COLUMN trigger_shadow_side VARCHAR(16) NULL
        COMMENT '影子变更侧：REPORTED/DESIRED/ANY（仅 SHADOW 源）' AFTER trigger_event_type;
