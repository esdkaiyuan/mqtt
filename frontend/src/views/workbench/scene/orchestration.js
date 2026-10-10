/**
 * 场景编排画布的投影与回写（T-29 设计文档 §4 / §5.1 / §10.2）。
 *
 * 画布是既有场景表单 `form` 的图形态投影，本模块是唯一的状态换算入口：
 * - `projectScene(form)`：把 `form` 派生为 `{ nodes, edges }`（只读渲染）；
 * - `applySceneEvent(form, event)`：把画布事件就地写回 `form.conditions` / `form.steps` / 触发字段。
 *
 * 不引入第二份状态，不新增校验口径；节点文案与缺省值一律复用 `utils/scene.js` 的既有函数。
 */

import { NODE_TYPE, MAX_CONDITIONS, MAX_STEPS, formatDelayEdgeLabel } from '@/utils/orchestration'
import {
  ACTION_TYPE_LABELS,
  CONDITION_LOGIC_LABELS,
  TRIGGER_SOURCE_LABELS,
  TRIGGER_SOURCE_TAG_TYPES,
  createActionConfig,
  createCondition,
  createStep,
  formatActionSummary,
  formatCondition,
  formatTriggerSummary,
  normalizeStep,
  normalizeTriggerSource,
  withNormalizedSeq
} from '@/utils/scene'

/** 默认布局：触发居左，条件组居中，步骤向右延伸并按行折返（坐标仅存会话，不落库）。 */
const LAYOUT = {
  trigger: { x: 0, y: 0 },
  condition: { x: 300, gapY: 150 },
  conditionGroup: { x: 620, y: 0 },
  step: { x: 960, gapX: 300, perRow: 3, gapY: 190 }
}

const EDGE_TYPE = 'smoothstep'

/** 允许经属性面板回写的字段白名单，避免事件携带无关键污染 `form`。 */
const TRIGGER_FIELDS = [
  'triggerType',
  'triggerDeviceId',
  'triggerIdentifier',
  'triggerOperator',
  'triggerThreshold',
  'thresholdEnabled',
  'triggerEventType',
  'timerCron'
]
const CONDITION_FIELDS = ['deviceId', 'identifier', 'operator', 'threshold']
const STEP_FIELDS = ['delaySeconds', 'targetType', 'targetMode', 'targetConfig', 'actionConfig', 'enabled']

function conditionPosition(index) {
  return { x: LAYOUT.condition.x, y: index * LAYOUT.condition.gapY }
}

function stepPosition(index) {
  const column = index % LAYOUT.step.perRow
  const row = Math.floor(index / LAYOUT.step.perRow)
  return { x: LAYOUT.step.x + column * LAYOUT.step.gapX, y: row * LAYOUT.step.gapY }
}

function asArray(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}

function triggerNode(form) {
  const triggerType = form.triggerType || 'PROPERTY'
  return {
    id: NODE_TYPE.TRIGGER,
    type: NODE_TYPE.TRIGGER,
    position: { ...LAYOUT.trigger },
    draggable: false,
    data: {
      key: NODE_TYPE.TRIGGER,
      title: '触发',
      summary: formatTriggerSummary(form),
      status: {
        label: TRIGGER_SOURCE_LABELS[triggerType] || triggerType,
        type: TRIGGER_SOURCE_TAG_TYPES[triggerType] || 'primary'
      }
    }
  }
}

function conditionNode(condition, index) {
  return {
    id: `condition-${index}`,
    type: NODE_TYPE.CONDITION,
    position: conditionPosition(index),
    draggable: false,
    data: {
      key: `condition-${index}`,
      title: `条件 ${index + 1}`,
      summary: formatCondition(condition),
      status: { label: condition.deviceId ? '指定设备' : '触发设备' },
      removable: true
    }
  }
}

function conditionGroupNode(conditions) {
  return {
    id: NODE_TYPE.CONDITION_GROUP,
    type: NODE_TYPE.CONDITION_GROUP,
    position: { ...LAYOUT.conditionGroup },
    draggable: false,
    data: {
      key: NODE_TYPE.CONDITION_GROUP,
      title: '条件组',
      summary: CONDITION_LOGIC_LABELS[conditions.logic] || conditions.logic,
      status: { label: `${conditions.count} 项`, type: 'primary' }
    }
  }
}

function stepNode(step, index) {
  return {
    id: `step-${index}`,
    type: NODE_TYPE.STEP,
    position: stepPosition(index),
    draggable: true,
    data: {
      key: `step-${index}`,
      title: `步骤 ${index + 1}`,
      summary: formatActionSummary(step),
      status: step.enabled
        ? { label: ACTION_TYPE_LABELS[step.actionType] || '启用', type: 'success' }
        : { label: '已停用', type: 'danger' },
      removable: true,
      reorderable: true
    }
  }
}

/**
 * `form` → `{ nodes, edges }`。连线由模型语义自动生成，不开放任意连边：
 * 条件节点汇聚条件组，条件组（无条件项时退化为触发）连到步骤 1，步骤按 seq 成链。
 */
export function projectScene(form) {
  if (!form) return { nodes: [], edges: [] }

  const conditions = asArray(form.conditions)
  const steps = asArray(form.steps)
  const nodes = [triggerNode(form)]
  const edges = []
  const hasConditions = conditions.length > 0

  if (hasConditions) {
    conditions.forEach((condition, index) => {
      nodes.push(conditionNode(condition, index))
      edges.push({
        id: `condition-${index}-${NODE_TYPE.CONDITION_GROUP}`,
        source: `condition-${index}`,
        target: NODE_TYPE.CONDITION_GROUP,
        type: EDGE_TYPE
      })
    })
    nodes.push(
      conditionGroupNode({ logic: form.conditionLogic, count: conditions.length })
    )
    edges.push({
      id: `${NODE_TYPE.TRIGGER}-${NODE_TYPE.CONDITION_GROUP}`,
      source: NODE_TYPE.TRIGGER,
      target: NODE_TYPE.CONDITION_GROUP,
      type: EDGE_TYPE
    })
  }

  steps.forEach((step, index) => {
    nodes.push(stepNode(step, index))
  })

  if (steps.length > 0) {
    const head = hasConditions ? NODE_TYPE.CONDITION_GROUP : NODE_TYPE.TRIGGER
    edges.push({
      id: `${head}-step-0`,
      source: head,
      target: 'step-0',
      type: EDGE_TYPE
    })
    for (let index = 1; index < steps.length; index += 1) {
      edges.push({
        id: `step-${index - 1}-step-${index}`,
        source: `step-${index - 1}`,
        target: `step-${index}`,
        type: EDGE_TYPE,
        label: formatDelayEdgeLabel(steps[index].delaySeconds)
      })
    }
  }

  return { nodes, edges }
}

function parseIndex(id, prefix) {
  if (typeof id !== 'string' || !id.startsWith(`${prefix}-`)) return -1
  const raw = id.slice(prefix.length + 1)
  return /^\d+$/.test(raw) ? Number(raw) : -1
}

function assignFields(target, patch, fields) {
  fields.forEach((field) => {
    if (field in patch) target[field] = patch[field]
  })
}

/** 落点位置换算为步骤索引：取默认布局下距离最近的一格。 */
function nearestStepIndex(position, count) {
  if (!position || count <= 0) return -1
  let best = 0
  let bestDistance = Infinity
  for (let index = 0; index < count; index += 1) {
    const point = stepPosition(index)
    const distance = Math.abs(point.x - position.x) + Math.abs(point.y - position.y)
    if (distance < bestDistance) {
      bestDistance = distance
      best = index
    }
  }
  return best
}

function addNode(form, event, timerSource) {
  if (event.nodeType === NODE_TYPE.CONDITION) {
    const conditions = asArray(form.conditions)
    if (conditions.length >= MAX_CONDITIONS) return false
    form.conditions = [...conditions, createCondition()]
    return true
  }
  if (event.nodeType === NODE_TYPE.STEP) {
    const steps = asArray(form.steps)
    if (steps.length >= MAX_STEPS) return false
    form.steps = withNormalizedSeq([...steps, createStep(timerSource)])
    return true
  }
  return false
}

function removeNode(form, event) {
  const conditionIndex = parseIndex(event.id, 'condition')
  if (conditionIndex >= 0) {
    const conditions = asArray(form.conditions)
    if (conditionIndex >= conditions.length) return false
    form.conditions = conditions.filter((_, index) => index !== conditionIndex)
    return true
  }

  const stepIndex = parseIndex(event.id, 'step')
  if (stepIndex >= 0) {
    const steps = asArray(form.steps)
    if (stepIndex >= steps.length) return false
    form.steps = withNormalizedSeq(steps.filter((_, index) => index !== stepIndex))
    return true
  }
  return false
}

function reorderStep(form, event) {
  const steps = asArray(form.steps)
  const from = parseIndex(event.id, 'step')
  if (from < 0 || from >= steps.length) return false
  const to = nearestStepIndex(event.position, steps.length)
  if (to < 0 || to === from) return false

  const next = [...steps]
  const [moved] = next.splice(from, 1)
  next.splice(to, 0, moved)
  form.steps = withNormalizedSeq(next)
  return true
}

function updateStep(step, patch, timerSource) {
  const nextActionType = patch.actionType
  if (nextActionType && nextActionType !== step.actionType) {
    step.actionType = nextActionType
    step.actionConfig = createActionConfig(nextActionType)
  }
  assignFields(step, patch, STEP_FIELDS)
  normalizeStep(step, timerSource)
}

function updateNode(form, event, timerSource) {
  const patch = event.patch
  if (!patch || typeof patch !== 'object') return false

  if (event.id === NODE_TYPE.TRIGGER) {
    assignFields(form, patch, TRIGGER_FIELDS)
    // 切换触发方式时清理其他源专属字段，与表单视图共用同一份口径（T-29 §5.1 / §10.5）
    if ('triggerType' in patch) normalizeTriggerSource(form)
    return true
  }
  if (event.id === NODE_TYPE.CONDITION_GROUP) {
    if ('conditionLogic' in patch) form.conditionLogic = patch.conditionLogic
    return true
  }

  const conditionIndex = parseIndex(event.id, 'condition')
  if (conditionIndex >= 0) {
    const condition = asArray(form.conditions)[conditionIndex]
    if (!condition) return false
    assignFields(condition, patch, CONDITION_FIELDS)
    return true
  }

  const stepIndex = parseIndex(event.id, 'step')
  if (stepIndex >= 0) {
    const step = asArray(form.steps)[stepIndex]
    if (!step) return false
    updateStep(step, patch, timerSource)
    return true
  }
  return false
}

/**
 * 画布事件就地写回 `form`。
 *
 * `event` 形状：`{ type: 'node-add', nodeType }` / `{ type: 'node-remove', id }` /
 * `{ type: 'node-reorder', id, position }` / `{ type: 'node-update', id, patch }`。
 * 返回是否改动了 `form`（上限拒绝 / 越界 / 未知事件均返回 `false`，调用方据此提示）。
 */
export function applySceneEvent(form, event) {
  if (!form || !event) return false
  const timerSource = form.triggerType === 'TIMER'

  switch (event.type) {
    case 'node-add':
      return addNode(form, event, timerSource)
    case 'node-remove':
      return removeNode(form, event)
    case 'node-reorder':
      return reorderStep(form, event)
    case 'node-update':
      return updateNode(form, event, timerSource)
    default:
      return false
  }
}