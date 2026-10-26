/**
 * 场景联动的展示映射与格式化（T-23）。
 *
 * 触发源 / 动作类型 / 目标类型 / 执行状态 / 步骤状态的文案与标签色集中在此，
 * 供场景列表、场景表单弹窗、步骤编辑器与执行记录抽屉复用，避免口径漂移。
 */

export const TRIGGER_SOURCE_LABELS = {
  PROPERTY: '属性触发',
  EVENT: '事件触发',
  TIMER: '定时触发',
  ONLINE: '设备上线',
  OFFLINE: '设备下线',
  SHADOW: '影子变更'
}

export const TRIGGER_SOURCE_TAG_TYPES = {
  PROPERTY: 'primary',
  EVENT: 'warning',
  TIMER: 'success',
  ONLINE: 'success',
  OFFLINE: 'info',
  SHADOW: 'warning'
}

export const SCENE_STATUS_LABELS = {
  ENABLED: '已启用',
  DISABLED: '已停用'
}

export const SCENE_STATUS_TAG_TYPES = {
  ENABLED: 'success',
  DISABLED: 'info'
}

export const CONDITION_LOGIC_LABELS = {
  AND: '满足全部条件',
  OR: '满足任一条件'
}

export const ACTION_TYPE_LABELS = {
  UPDATE_PROPERTY: '更新云端属性',
  SEND_COMMAND: '下发命令',
  FORWARD_MQTT: '转发 MQTT',
  FORWARD_HTTP: '转发 HTTP',
  FORWARD_KAFKA: '转发 Kafka',
  BRANCH: '条件分支',
  LOOP: '循环结束',
  PARALLEL: '并行开始',
  JOIN: '并行汇合'
}

export const TARGET_TYPE_LABELS = {
  TRIGGER: '触发设备',
  FIXED: '指定设备',
  NONE: '无目标'
}

export const OPERATOR_LABELS = {
  GT: '>',
  GTE: '≥',
  LT: '<',
  LTE: '≤',
  EQ: '=',
  NE: '≠'
}

export const EXECUTION_STATUS_LABELS = {
  PENDING: '待执行',
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败'
}

export const EXECUTION_STATUS_TAG_TYPES = {
  PENDING: 'warning',
  RUNNING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger'
}

export const STEP_STATUS_LABELS = {
  PENDING: '待执行',
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '已跳过'
}

export const STEP_STATUS_TAG_TYPES = {
  PENDING: 'warning',
  RUNNING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
  SKIPPED: 'info'
}

export const TRIGGER_SOURCE_OPTIONS = [
  { value: 'PROPERTY', label: '属性触发' },
  { value: 'EVENT', label: '事件触发' },
  { value: 'TIMER', label: '定时触发' },
  { value: 'ONLINE', label: '设备上线' },
  { value: 'OFFLINE', label: '设备下线' },
  { value: 'SHADOW', label: '影子变更' }
]

/** 影子变更侧：场景可配置 ANY，试运行只能选真实变更侧。 */
export const SHADOW_SIDE_LABELS = {
  REPORTED: '设备上报',
  DESIRED: '云端期望',
  ANY: '任意变更'
}

export const SHADOW_SIDE_OPTIONS = [
  { value: 'REPORTED', label: '设备上报（reported）' },
  { value: 'DESIRED', label: '云端期望（desired）' },
  { value: 'ANY', label: '任意变更' }
]

export const SHADOW_TEST_SIDE_OPTIONS = [
  { value: 'REPORTED', label: '设备上报（reported）' },
  { value: 'DESIRED', label: '云端期望（desired）' }
]

export const CONDITION_LOGIC_OPTIONS = [
  { value: 'AND', label: '满足全部条件' },
  { value: 'OR', label: '满足任一条件' }
]

export const ACTION_TYPE_OPTIONS = [
  { value: 'UPDATE_PROPERTY', label: '更新云端属性' },
  { value: 'SEND_COMMAND', label: '下发命令' },
  { value: 'FORWARD_MQTT', label: '转发 MQTT' },
  { value: 'FORWARD_HTTP', label: '转发 HTTP' },
  { value: 'FORWARD_KAFKA', label: '转发 Kafka' },
  { value: 'BRANCH', label: '条件分支' },
  { value: 'LOOP', label: '循环结束' },
  { value: 'PARALLEL', label: '并行开始' },
  { value: 'JOIN', label: '并行汇合' }
]

export const TARGET_TYPE_OPTIONS = [
  { value: 'TRIGGER', label: '触发设备' },
  { value: 'FIXED', label: '指定设备' }
]

export const OPERATOR_OPTIONS = [
  { value: 'GT', label: '大于 (>)' },
  { value: 'GTE', label: '大于等于 (≥)' },
  { value: 'LT', label: '小于 (<)' },
  { value: 'LTE', label: '小于等于 (≤)' },
  { value: 'EQ', label: '等于 (=)' },
  { value: 'NE', label: '不等于 (≠)' }
]

export const EVENT_TYPE_OPTIONS = [
  { value: 'info', label: 'info' },
  { value: 'alert', label: 'alert' },
  { value: 'fault', label: 'fault' }
]

export const EXECUTION_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待执行' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' }
]

export const STEP_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待执行' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'SKIPPED', label: '已跳过' }
]

/** 转发类动作只允许「触发设备」，其 target_config 必须为空。 */
export const FORWARD_ACTIONS = new Set(['FORWARD_MQTT', 'FORWARD_HTTP', 'FORWARD_KAFKA'])

/** 上下线触发源：只按设备状态变化触发，不配置标识符 / 阈值 / cron。 */
export const STATUS_TRIGGERS = new Set(['ONLINE', 'OFFLINE'])

/** 流程步骤：分支 / 循环 / 并行不执行动作，也没有目标设备。 */
export const FLOW_STEPS = new Set(['BRANCH', 'LOOP', 'PARALLEL', 'JOIN'])

/** 并行块分支数上下限（与后端 SceneConstants 保持一致）。 */
export const MIN_PARALLEL_BRANCHES = 2
export const MAX_PARALLEL_BRANCHES = 8

export function isForwardAction(actionType) {
  return FORWARD_ACTIONS.has(actionType)
}

export function isStatusTrigger(triggerType) {
  return STATUS_TRIGGERS.has(triggerType)
}

export function isShadowTrigger(triggerType) {
  return triggerType === 'SHADOW'
}

export function isFlowStep(actionType) {
  return FLOW_STEPS.has(actionType)
}

/** 把可清空下拉的 `''` / `undefined` 统一归一为 null，保证流程引用类型稳定。 */
export function seqOrNull(value) {
  return Number.isInteger(value) ? value : null
}

/** 场景触发条件的中文摘要，按触发源拼装。 */
export function formatTriggerSummary(scene) {
  if (!scene) return '—'
  switch (scene.triggerType) {
    case 'ONLINE':
    case 'OFFLINE':
      return TRIGGER_SOURCE_LABELS[scene.triggerType]
    case 'SHADOW': {
      const side = SHADOW_SIDE_LABELS[scene.triggerShadowSide] || SHADOW_SIDE_LABELS.REPORTED
      return `影子变更（${side}）${scene.triggerIdentifier ? ` · ${scene.triggerIdentifier}` : ''}`
    }
    case 'EVENT':
      return scene.triggerEventType
        ? `${scene.triggerIdentifier ?? '—'}（${scene.triggerEventType}）`
        : (scene.triggerIdentifier ?? '—')
    case 'TIMER':
      return scene.timerCron ? `定时 ${scene.timerCron}` : '定时'
    case 'PROPERTY':
    default:
      if (!scene.triggerOperator && !scene.triggerThreshold) {
        return `${scene.triggerIdentifier ?? '—'} 上报`
      }
      return `${scene.triggerIdentifier ?? '—'} ${OPERATOR_LABELS[scene.triggerOperator] ?? scene.triggerOperator ?? ''} ${scene.triggerThreshold ?? '—'}`
  }
}

/** 单条步骤动作的中文摘要，按动作类型读取 actionConfig。 */
export function formatActionSummary(step) {
  if (!step) return '—'
  const config = step.actionConfig || {}
  switch (step.actionType) {
    case 'UPDATE_PROPERTY':
      return `写入 ${config.identifier ?? '—'} = ${config.value ?? '—'}`
    case 'SEND_COMMAND':
      return `${config.commandType === 'service' ? '服务调用' : '属性设置'} → ${config.identifier ?? '—'}`
    case 'FORWARD_MQTT':
      return `MQTT → ${config.topic ?? '—'}`
    case 'FORWARD_HTTP':
      return `${(config.method || 'POST').toUpperCase()} ${config.url ?? '—'}`
    case 'FORWARD_KAFKA':
      return `Kafka → ${config.topic ?? '—'}${config.key ? `（key=${config.key}）` : ''}`
    case 'BRANCH':
      return `条件分支 → 真：${config.trueSeq == null ? '结束' : `第 ${config.trueSeq} 步`} / 假：${config.falseSeq == null ? '结束' : `第 ${config.falseSeq} 步`}`
    case 'LOOP':
      return `循环 ${config.times ?? '—'} 次（回到第 ${config.startSeq ?? '—'} 步）`
    case 'PARALLEL':
      return `并行 ${config.branches ?? '—'} 路`
    case 'JOIN':
      return '并行汇合'
    default:
      return ACTION_TYPE_LABELS[step.actionType] || step.actionType || '—'
  }
}

/** 条件项的中文摘要。deviceId 为空表示作用于触发设备。 */
export function formatCondition(condition) {
  if (!condition) return '—'
  const operator = OPERATOR_LABELS[condition.operator] ?? condition.operator ?? ''
  return `${condition.identifier ?? '—'} ${operator} ${condition.threshold ?? '—'}`
}

/** 时间戳格式化为本地时间，空值回退占位符。 */
export function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

/**
 * 解析 JSON 对象文本，供动作参数 / 模板字段输入使用。
 * 空文本返回 undefined（表示缺省）；非法 JSON 或非对象返回 { __error }。
 */
export function parseJsonObject(text, label = 'JSON') {
  const raw = typeof text === 'string' ? text.trim() : ''
  if (!raw) return { value: undefined }
  try {
    const parsed = JSON.parse(raw)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { error: `${label} 需为 JSON 对象` }
    }
    return { value: parsed }
  } catch {
    return { error: `${label} 不是合法 JSON` }
  }
}

/**
 * 条件项 / 步骤 / 目标的构造与归一化（T-29 从 SceneConditionEditor / SceneStepEditor 抽出）。
 *
 * 表单视图与画布视图共用这一份口径，避免缺省值与联动清理两处漂移。
 * `createStep` 与 `normalizeStep` 原依赖组件 `props.timerSource`，抽到纯模块后
 * 把 `timerSource` 提升为显式入参（TIMER 触发源下无触发设备，目标锁 FIXED）。
 */

/** 条件项缺省形状；deviceId 为空表示作用于触发设备。 */
export function createCondition() {
  return { deviceId: null, identifier: '', operator: 'GT', threshold: '' }
}

/** 步骤目标维度缺省形状（四个维度互斥，只允许一个非空）。 */
export function createTargetConfig() {
  return { deviceIds: [], productIds: [], groupIds: [], tagIds: [] }
}

/** 各动作类型的 action_config 缺省形状（场景设计文档 §5.3 / T-44 分支与循环）。 */
export function createActionConfig(actionType) {
  switch (actionType) {
    case 'SEND_COMMAND':
      return { commandType: 'property_set', identifier: '', params: '' }
    case 'FORWARD_MQTT':
      return { topic: '', qos: 1, payloadTemplate: '' }
    case 'FORWARD_HTTP':
      return { url: '', method: 'POST', headers: '', payloadTemplate: '' }
    case 'FORWARD_KAFKA':
      return { topic: '', key: '', payloadTemplate: '' }
    case 'BRANCH':
      return {
        deviceId: null,
        identifier: '',
        operator: 'GT',
        threshold: '',
        trueSeq: null,
        falseSeq: null
      }
    case 'LOOP':
      return { startSeq: null, times: 3 }
    case 'PARALLEL':
      return { branches: 2 }
    case 'JOIN':
      return {}
    case 'UPDATE_PROPERTY':
    default:
      return { identifier: '', value: '' }
  }
}

/** 步骤缺省形状。timerSource 为真时目标默认 FIXED（无触发设备可作用）。 */
export function createStep(timerSource = false) {
  return {
    seq: 0,
    delaySeconds: 0,
    actionType: 'UPDATE_PROPERTY',
    targetType: timerSource ? 'FIXED' : 'TRIGGER',
    targetMode: 'device',
    targetConfig: createTargetConfig(),
    actionConfig: createActionConfig('UPDATE_PROPERTY'),
    parallelSeq: null,
    branchIndex: null,
    enabled: true
  }
}

/** 编辑既有场景时，按已选中的维度推断目标模式。 */
export function resolveTargetMode(config) {
  if (!config) return 'device'
  if (config.productIds?.length) return 'product'
  if (config.groupIds?.length) return 'group'
  if (config.tagIds?.length) return 'tag'
  return 'device'
}

/** 修正步骤的目标类型与 target_config，保证与触发源 / 动作类型约束一致。 */
export function normalizeStep(step, timerSource = false) {
  if (isFlowStep(step.actionType)) {
    step.targetType = 'NONE'
    step.targetConfig = null
    step.parallelSeq = null
    step.branchIndex = null
    return
  }
  if (step.targetType === 'NONE') {
    step.targetType = timerSource ? 'FIXED' : 'TRIGGER'
  }
  if (isForwardAction(step.actionType)) {
    step.targetType = 'TRIGGER'
    step.targetConfig = null
    return
  }
  if (!step.targetConfig || typeof step.targetConfig !== 'object') {
    step.targetConfig = createTargetConfig()
  }
  if (timerSource) {
    step.targetType = 'FIXED'
  }
  if (!step.targetMode) {
    step.targetMode = resolveTargetMode(step.targetConfig)
  }
}

/**
 * 切换触发方式时清理其他源专属字段（场景设计文档 §7.2）。
 *
 * 表单视图的 `handleTriggerTypeChange` 与画布触发节点的属性面板共用这一份口径，
 * 避免两处联动清理漂移（T-29 全局约束）。
 */
export function normalizeTriggerSource(form) {
  if (!form) return
  if (form.triggerType === 'TIMER') {
    form.triggerDeviceId = null
    form.triggerIdentifier = ''
    form.triggerOperator = null
    form.triggerThreshold = ''
    form.thresholdEnabled = false
    form.triggerEventType = ''
    return
  }
  form.timerCron = ''
  if (form.triggerType === 'EVENT') {
    form.triggerOperator = null
    form.triggerThreshold = ''
    form.thresholdEnabled = false
  }
  if (isStatusTrigger(form.triggerType)) {
    form.triggerIdentifier = ''
    form.triggerOperator = null
    form.triggerThreshold = ''
    form.thresholdEnabled = false
    form.triggerEventType = ''
  }
  if (isShadowTrigger(form.triggerType)) {
    form.triggerOperator = null
    form.triggerThreshold = ''
    form.thresholdEnabled = false
    form.triggerEventType = ''
    form.triggerShadowSide = form.triggerShadowSide || 'REPORTED'
  }
}

/** 步骤序号归一化为 1..N 连续，避免删除 / 移动后出现空洞（场景设计文档 §7.4）。 */
export function withNormalizedSeq(items) {
  return items.map((step, index) => {
    step.seq = index + 1
    return step
  })
}

/**
 * 分支 / 循环引用重映射（T-44）。
 *
 * 步骤增删 / 移动后，把 actionConfig 中记录的目标序号按旧序号 → 新序号的映射改写，
 * 避免用户调整顺序后分支跳到错误步骤。映射返回 null 表示引用已失效。
 */
export function remapFlowRefs(steps, mapping) {
  if (!Array.isArray(steps) || typeof mapping !== 'function') return
  steps.forEach((step) => {
    if (!step || !isFlowStep(step.actionType) || !step.actionConfig) return
    const config = step.actionConfig
    if (step.actionType === 'BRANCH') {
      config.trueSeq = remapSeq(config.trueSeq, mapping)
      config.falseSeq = remapSeq(config.falseSeq, mapping)
    } else if (step.actionType === 'LOOP') {
      config.startSeq = remapSeq(config.startSeq, mapping)
    }
  })
}

/** 删除第 removedIndex 步后的引用重映射：被删步骤的引用置空，其后的序号整体减一。 */
export function remapFlowRefsAfterRemove(steps, removedIndex) {
  const removedSeq = removedIndex + 1
  remapFlowRefs(steps, (seq) => (seq === removedSeq ? null : seq > removedSeq ? seq - 1 : seq))
}

/** 把第 fromIndex 步移动到 toIndex 后的引用重映射（按旧序号计算）。 */
export function remapFlowRefsAfterMove(steps, fromIndex, toIndex) {
  const fromSeq = fromIndex + 1
  const toSeq = toIndex + 1
  remapFlowRefs(steps, (seq) => {
    if (seq === fromSeq) return toSeq
    if (fromSeq < toSeq && seq > fromSeq && seq <= toSeq) return seq - 1
    if (fromSeq > toSeq && seq >= toSeq && seq < fromSeq) return seq + 1
    return seq
  })
}

/**
 * 扫描并行块。返回已闭合的块与尚未闭合的块；嵌套 / 孤立汇合由 `validateParallelStructure` 拒绝。
 * 分支归属按步骤在数组中的位置计算，不依赖可能过期的 `parallelSeq` 快照。
 */
export function scanParallelBlocks(items) {
  const steps = Array.isArray(items) ? items : []
  const blocks = []
  let open = null
  let nested = false
  let orphanJoin = false
  steps.forEach((step, index) => {
    if (!step) return
    if (step.actionType === 'PARALLEL') {
      if (open) {
        nested = true
        return
      }
      open = {
        startIndex: index,
        startSeq: index + 1,
        branches: normalizeBranchCount(step.actionConfig?.branches),
        members: []
      }
      return
    }
    if (step.actionType === 'JOIN') {
      if (!open) {
        orphanJoin = true
        return
      }
      const block = {
        ...open,
        joinIndex: index,
        joinSeq: index + 1
      }
      blocks.push(block)
      open = null
      return
    }
    if (open) {
      const branch = normalizeBranchIndex(step.branchIndex, open.branches)
      if (!open.members[branch]) open.members[branch] = []
      open.members[branch].push(index)
    }
  })
  return { blocks, open, nested, orphanJoin }
}

/** 当前步骤所属的并行块（供编辑器显示分支选择器）。 */
export function parallelInfoAt(items, index) {
  const { blocks, open } = scanParallelBlocks(items)
  const block = blocks.find((item) => item.startIndex < index && index < item.joinIndex)
    || (open && index > open.startIndex ? open : null)
  if (!block) return null
  return {
    startIndex: block.startIndex,
    startSeq: block.startSeq,
    branches: block.branches
  }
}

/**
 * 按当前步骤顺序重算并行归属：块内普通步骤写入 `parallelSeq` / `branchIndex`，
 * 块外与流程步骤清空。返回同一个数组，便于表单提交前统一归一化。
 */
export function normalizeParallelMembership(items) {
  const steps = Array.isArray(items) ? items : []
  let open = null
  steps.forEach((step, index) => {
    if (!step) return
    if (step.actionType === 'PARALLEL') {
      step.parallelSeq = null
      step.branchIndex = null
      if (!open) {
        open = { startSeq: index + 1, branches: normalizeBranchCount(step.actionConfig?.branches) }
      }
      return
    }
    if (step.actionType === 'JOIN') {
      step.parallelSeq = null
      step.branchIndex = null
      if (open) open = null
      return
    }
    if (open) {
      step.parallelSeq = open.startSeq
      step.branchIndex = normalizeBranchIndex(step.branchIndex, open.branches)
    } else {
      step.parallelSeq = null
      step.branchIndex = null
    }
  })
  return steps
}

/** 并行结构的前端校验；返回空串表示通过。 */
export function validateParallelStructure(items) {
  const steps = Array.isArray(items) ? items : []
  const blocks = []
  let open = null
  for (let index = 0; index < steps.length; index += 1) {
    const step = steps[index]
    if (!step) continue
    if (step.actionType === 'PARALLEL') {
      if (open) return `第 ${index + 1} 步：并行块不能嵌套`
      if (step.enabled === false) return `第 ${index + 1} 步：并行开始步骤必须启用`
      const branches = Number(step.actionConfig?.branches)
      if (!Number.isInteger(branches) || branches < MIN_PARALLEL_BRANCHES || branches > MAX_PARALLEL_BRANCHES) {
        return `第 ${index + 1} 步：并行分支数需在 ${MIN_PARALLEL_BRANCHES} ~ ${MAX_PARALLEL_BRANCHES}`
      }
      open = { startIndex: index, startSeq: index + 1, branches, counts: Array(branches).fill(0) }
      continue
    }
    if (step.actionType === 'JOIN') {
      if (step.enabled === false) return `第 ${index + 1} 步：并行汇合步骤必须启用`
      if (!open) return `第 ${index + 1} 步：并行汇合没有对应的并行开始`
      for (let branch = 0; branch < open.branches; branch += 1) {
        if (open.counts[branch] === 0) {
          return `第 ${open.startSeq} 步的并行分支 ${branch + 1} 至少需要一个启用步骤`
        }
      }
      blocks.push({
        startSeq: open.startSeq,
        joinSeq: index + 1,
        branches: open.branches
      })
      open = null
      continue
    }
    if (open) {
      if (FLOW_STEPS.has(step.actionType)) {
        return `第 ${index + 1} 步：并行块内只允许普通动作步骤`
      }
      const branch = Number(step.branchIndex)
      if (!Number.isInteger(branch) || branch < 0 || branch >= open.branches) {
        return `第 ${index + 1} 步：并行分支需在 1 ~ ${open.branches}`
      }
      if (step.enabled !== false) open.counts[branch] += 1
    }
  }
  if (open) return `第 ${open.startSeq} 步的并行开始缺少对应的并行汇合`

  for (const step of steps) {
    if (!step) continue
    if (step.actionType === 'BRANCH') {
      const targets = [step.actionConfig?.trueSeq, step.actionConfig?.falseSeq]
      for (const target of targets) {
        if (!Number.isInteger(target)) continue
        if (blocks.some((block) => block.startSeq <= target && target <= block.joinSeq)) {
          return `第 ${step.seq} 步：分支不能跳入并行块`
        }
      }
    }
    if (step.actionType === 'LOOP') {
      const startSeq = Number(step.actionConfig?.startSeq)
      const loopSeq = Number(step.seq)
      if (!Number.isInteger(startSeq) || !Number.isInteger(loopSeq)) continue
      const overlap = blocks.find((block) => startSeq <= block.joinSeq && block.startSeq <= loopSeq)
      if (overlap) {
        return `第 ${loopSeq} 步：循环体不能与第 ${overlap.startSeq}–${overlap.joinSeq} 步的并行块重叠`
      }
    }
  }
  return ''
}

function normalizeBranchCount(value) {
  const branches = Number(value)
  if (!Number.isInteger(branches)) return MIN_PARALLEL_BRANCHES
  return Math.min(Math.max(branches, MIN_PARALLEL_BRANCHES), MAX_PARALLEL_BRANCHES)
}

function normalizeBranchIndex(value, branches) {
  const branch = Number(value)
  if (!Number.isInteger(branch) || branch < 0 || branch >= branches) return 0
  return branch
}

function remapSeq(value, mapping) {
  if (typeof value !== 'number') return value
  return mapping(value)
}
