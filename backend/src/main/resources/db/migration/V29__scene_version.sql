-- T-51：场景版本快照与回滚。
-- 每次创建 / 更新 / 回滚保存完整快照，最多保留最近 20 个版本。

CREATE TABLE IF NOT EXISTS scene_version (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scene_id     BIGINT       NOT NULL COMMENT '场景ID',
    version_no   INT          NOT NULL COMMENT '场景内递增版本号',
    change_type  VARCHAR(16)  NOT NULL COMMENT '变更类型：CREATE/UPDATE/RESTORE',
    scene_name   VARCHAR(64)  NOT NULL COMMENT '快照场景名称',
    trigger_type VARCHAR(16)  NOT NULL COMMENT '快照触发源',
    step_count   INT          NOT NULL COMMENT '快照步骤数',
    snapshot     MEDIUMTEXT   NOT NULL COMMENT '完整快照 JSON',
    operator_id  BIGINT       NULL COMMENT '操作者用户ID',
    created_at   DATETIME(3)  NOT NULL COMMENT '保存时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scene_version (scene_id, version_no),
    KEY idx_scene_version_scene (scene_id, version_no),
    CONSTRAINT fk_scene_version_scene FOREIGN KEY (scene_id)
        REFERENCES scene_definition(id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '场景版本快照';
