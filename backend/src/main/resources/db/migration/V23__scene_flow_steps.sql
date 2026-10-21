-- T-44：场景步骤分支 / 循环。
-- 步骤类型与目标类型仅扩展注释（列宽已足够）；执行明细增加循环轮次与流程结果快照。

ALTER TABLE scene_step
    MODIFY COLUMN action_type VARCHAR(32) NOT NULL
        COMMENT '步骤类型：UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP/BRANCH/LOOP',
    MODIFY COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'TRIGGER'
        COMMENT '目标：TRIGGER=触发设备 / FIXED=固定目标集合 / NONE=分支或循环步骤';

ALTER TABLE scene_step_run
    MODIFY COLUMN action_type VARCHAR(32) NOT NULL
        COMMENT '步骤类型快照：UPDATE_PROPERTY/SEND_COMMAND/FORWARD_MQTT/FORWARD_HTTP/BRANCH/LOOP',
    ADD COLUMN loop_iteration INT NOT NULL DEFAULT 0
        COMMENT '循环步骤当前迭代轮次（0 起；非循环步骤恒为 0）' AFTER forward_payload,
    ADD COLUMN flow_result VARCHAR(512) NULL
        COMMENT '分支 / 循环流程结果快照' AFTER loop_iteration;
