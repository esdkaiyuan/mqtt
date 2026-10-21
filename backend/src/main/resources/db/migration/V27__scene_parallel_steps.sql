-- T-49：场景步骤并行。
-- PARALLEL 开始并行块，JOIN 汇合；块内普通动作步骤通过 parallel_seq + branch_index 归属分支。

ALTER TABLE scene_step
    ADD COLUMN parallel_seq INT NULL COMMENT '所属并行块起始步骤序号（PARALLEL 步骤 seq；非并行块为 NULL）' AFTER action_config,
    ADD COLUMN branch_index INT NULL COMMENT '并行分支序号（0 起；非并行块为 NULL）' AFTER parallel_seq;

ALTER TABLE scene_step_run
    ADD COLUMN parallel_seq INT NULL COMMENT '并行块起始步骤序号快照（T-49）' AFTER seq,
    ADD COLUMN branch_index INT NULL COMMENT '并行分支序号快照（T-49）' AFTER parallel_seq;

ALTER TABLE scene_step
    MODIFY COLUMN action_type VARCHAR(32) NOT NULL
        COMMENT '动作类型：UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP/BRANCH/LOOP/PARALLEL/JOIN';

ALTER TABLE scene_step_run
    MODIFY COLUMN action_type VARCHAR(32) NOT NULL
        COMMENT '动作类型快照：UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP/BRANCH/LOOP/PARALLEL/JOIN';
