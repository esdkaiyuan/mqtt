/**
 * 场景联动的展示映射与格式化（T-23）。
 *
 * 触发源 / 动作类型 / 目标类型 / 执行状态 / 步骤状态的文案与标签色集中在此，
 * 供场景列表、场景表单弹窗、步骤编辑器与执行记录抽屉复用，避免口径漂移。
 */

export const TRIGGER_SOURCE_LABELS = {
  PROPERTY: '属性触发',
  EVENT: '事件触发',
  TIMER: '定时触发'
}

export const TRIGGER_SOURCE_TAG_TYPES = {
  PROPERTY: 'primary',
  EVENT: 'warning',
  TIMER: 'success'
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
  FORWARD_HTTP: '转发 HTTP'
}

export const TARGET_TYPE_LABELS = {
  TRIGGER: '触发设备',
  FIXED: '指定设备'
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
  { value: 'TIMER', label: '定时触发' }
]

export const CONDITION_LOGIC_OPTIONS = [
  { value: 'AND', label: '满足全部条件' },
  { value: 'OR', label: '满足任一条件' }
]

export const ACTION_TYPE_OPTIONS = [
  { value: 'UPDATE_PROPERTY', label: '更新云端属性' },
  { value: 'SEND_COMMAND', label: '下发命令' },
  { value: 'FORWARD_MQTT', label: '转发 MQTT' },
  { value: 'FORWARD_HTTP', label: '转发 HTTP' }
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
export const FORWARD_ACTIONS = new Set(['FORWARD_MQTT', 'FORWARD_HTTP'])

export function isForwardAction(actionType) {
  return FORWARD_ACTIONS.has(actionType)
}

/** 场景触发条件的中文摘要，按触发源拼装。 */
export function formatTriggerSummary(scene) {
  if (!scene) return '—'
  switch (scene.triggerType) {
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

/** 各动作类型的 action_config 缺省形状（场景设计文档 §5.3）。 */
export function createActionConfig(actionType) {
  switch (actionType) {
    case 'SEND_COMMAND':
      return { commandType: 'property_set', identifier: '', params: '' }
    case 'FORWARD_MQTT':
      return { topic: '', qos: 1, payloadTemplate: '' }
    case 'FORWARD_HTTP':
      return { url: '', method: 'POST', headers: '', payloadTemplate: '' }
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
  } else {
    form.timerCron = ''
    if (form.triggerType === 'EVENT') {
      form.triggerOperator = null
      form.triggerThreshold = ''
      form.thresholdEnabled = false
    }
  }
}

/** 步骤序号归一化为 1..N 连续，避免删除 / 移动后出现空洞（场景设计文档 §7.4）。 */
export function withNormalizedSeq(items) {
  return items.map((step, index) => {
    step.seq = index + 1
    return step
  })
}
