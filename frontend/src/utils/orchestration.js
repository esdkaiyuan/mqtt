/**
 * 编排画布（T-29）的共享常量与格式化。
 *
 * 画布是既有表单 `form` 的图形态投影（设计文档 §4.1），本模块只承载
 * 「节点类型 / 节点面板 / 边标签」这类与领域无关的画布常量；
 * 场景与规则的投影 / 回写逻辑分别放在各自视图目录（`projectScene` / `projectRule`），
 * 容器组件不承担领域知识。
 */

/** 编排类型：决定节点面板可用项与投影分支。 */
export const ORCHESTRATION_KIND = {
  SCENE: 'scene',
  RULE: 'rule'
}

/** 画布节点类型（设计文档 §4.2）。 */
export const NODE_TYPE = {
  TRIGGER: 'trigger',
  CONDITION_GROUP: 'conditionGroup',
  CONDITION: 'condition',
  STEP: 'step',
  SCOPE: 'scope',
  ACTION: 'action'
}

/** 与后端 app.rule 限制一致：单场景条件数与步骤数（设计文档 §10.4）。 */
export const MAX_CONDITIONS = 10
export const MAX_STEPS = 20

/** 节点面板拖拽到画布的 dataTransfer MIME 类型（面板写入、画布读取）。 */
export const NODE_DND_MIME = 'application/x-orchestration-node'

/**
 * 节点面板定义，按编排类型分组（设计文档 §4.4）。
 *
 * 只有「条件」「步骤」可从面板拖入；触发 / 条件组 / 作用域 / 动作节点恒存在，
 * 不出现在面板。`limit` 为该类型上限（达上限时面板项置灰，设计文档 §10.4），
 * `count(form)` 从 `form` 读当前数量。
 */
export const NODE_PALETTE = {
  [ORCHESTRATION_KIND.SCENE]: [
    {
      type: NODE_TYPE.CONDITION,
      label: '条件',
      description: '为条件组追加一条判定',
      limit: MAX_CONDITIONS,
      count: (form) => (Array.isArray(form.conditions) ? form.conditions.length : 0)
    },
    {
      type: NODE_TYPE.STEP,
      label: '步骤',
      description: '追加一步时序动作',
      limit: MAX_STEPS,
      count: (form) => (Array.isArray(form.steps) ? form.steps.length : 0)
    }
  ],
  // 规则三节点恒存在，无面板项（设计文档 §4.4）
  [ORCHESTRATION_KIND.RULE]: []
}

/** 取某编排类型的节点面板定义；未知类型返回空数组。 */
export function paletteFor(kind) {
  return NODE_PALETTE[kind] ?? []
}

/** 面板项是否已达上限（置灰依据，设计文档 §10.4）。 */
export function isPaletteItemDisabled(item, form) {
  if (!item || item.limit == null) return false
  const count = item.count ? item.count(form) : 0
  return count >= item.limit
}

/** 步骤链边的延时标签：`delaySeconds > 0` 才显示「延时 Ns」，否则无标签（设计文档 §4.3）。 */
export function formatDelayEdgeLabel(delaySeconds) {
  const seconds = Number(delaySeconds ?? 0)
  return seconds > 0 ? `延时 ${seconds}s` : ''
}

/** 规则动作边的冷却标签：`cooldownSeconds > 0` 才显示「冷却 Ns」，否则无标签（设计文档 §4.3）。 */
export function formatCooldownEdgeLabel(cooldownSeconds) {
  const seconds = Number(cooldownSeconds ?? 0)
  return seconds > 0 ? `冷却 ${seconds}s` : ''
}

/** 边标签格式化入口，供投影逻辑统一引用。 */
export const EDGE_LABEL = {
  delay: formatDelayEdgeLabel,
  cooldown: formatCooldownEdgeLabel
}