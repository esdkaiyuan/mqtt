-- ============================================================
-- V20：规则多条件与多动作（G-6-7，T-33）
-- 用途：
--   rule_definition 追加 condition_logic / conditions_json / actions_json 三列
-- 对应缺口：G-6-7「规则模型目前每条规则只有一个触发条件与一个动作」。
-- 说明：
--   - **只加列，不改既有列语义，不删列**（沿用 V16 的做法），应用版本可安全回滚。
--   - **JSON 列 + 旧列回落**是本次的核心选择：规则评估是「按 userId 拉全量规则 → 内存匹配」
--     （RuleDefinitionMapper.selectEnabledByUser），不依赖 SQL 对条件/动作做过滤，
--     因此 JSON 列**不损失任何查询能力**，却能让存量规则与存量代码路径行为逐位不变。
--   - 存量行回填后 condition_logic='AND'、conditions_json=NULL、actions_json=NULL，
--     评估时自动走旧列（identifier/operator/threshold_value 与 action_type/action_config），
--     与 V9 原始行为完全等价，**无需任何数据改写脚本**。
--   - 条件项 JSON：{"identifier","operator","thresholdValue","eventType"}
--     动作项 JSON：{"actionType","actionConfig"}
--   - 刻意不建 rule_condition / rule_action 子表：条件与动作永远整条规则一起读写，
--     从不单独按条件查库（见上），子表只会带来额外 JOIN 与一致性维护成本却换不到任何查询收益。
--   - 与 action_config / group_ids 沿用同一 TEXT + JSON 数组口径，不引入新类型。
-- ============================================================

ALTER TABLE rule_definition
    ADD COLUMN condition_logic VARCHAR(8) NOT NULL DEFAULT 'AND'
        COMMENT '多条件组合逻辑：AND=全部满足 / OR=任一满足；仅 conditions_json 非空时有意义',
    ADD COLUMN conditions_json TEXT NULL
        COMMENT '多条件 JSON 数组 [{identifier,operator,thresholdValue,eventType}]；NULL 时回落到 identifier/operator/threshold_value 单条件',
    ADD COLUMN actions_json TEXT NULL
        COMMENT '多动作 JSON 数组 [{actionType,actionConfig}]；NULL 时回落到 action_type/action_config 单动作';

-- 多动作为何需要执行快照列：
--   RuleActionExecutor.execute(executionId) 是首次执行 / 巡检重放 / 手动重试共用的唯一入口，
--   它按执行记录 ID 回查规则配置来取动作参数。若多动作的 N 条 execution 都只靠回查，
--   读到的永远是 actions_json 之外那个「单动作列」—— 第 2 个动作会执行成第 1 个动作。
--   把该动作的配置随触发快照写入执行记录，重试时从快照读而非从规则当前值读：
--   ① 各动作各自正确；② 规则被改后在途执行仍按触发时的配置跑完（单动作路径刻意留空走原逻辑，保持零回归）。
ALTER TABLE rule_execution
    ADD COLUMN action_config_snapshot TEXT NULL
        COMMENT '该次执行对应的动作配置快照（多动作时逐动作写入）；NULL 表示回查规则当前 action_config';