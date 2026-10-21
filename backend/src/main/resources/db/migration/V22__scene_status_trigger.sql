-- T-43：场景触发源扩展 ONLINE / OFFLINE。
-- 列宽 VARCHAR(16) 已足够，无需变更类型；仅同步列注释，避免库内口径与校验器不一致。
ALTER TABLE scene_definition
    MODIFY COLUMN trigger_type VARCHAR(16) NOT NULL
        COMMENT '触发源：PROPERTY/EVENT/TIMER/ONLINE/OFFLINE';
