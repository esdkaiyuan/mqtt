/**
 * 消息规则的展示映射与格式化（T-19）。
 *
 * 触发源 / 动作 / 状态 / 比较符的文案与标签色集中在此，
 * 供规则列表、规则编辑抽屉与执行记录三处复用，避免各写一份出现口径漂移。
 */

export const SOURCE_TYPE_LABELS = {
  PROPERTY: '属性',
  EVENT: '事件'
}

export const ACTION_TYPE_LABELS = {
  UPDATE_PROPERTY: '更新云端属性',
  SEND_COMMAND: '下发命令',
  FORWARD_MQTT: '转发 MQTT',
  FORWARD_HTTP: '转发 HTTP'
}

export const EXECUTION_STATUS_LABELS = {
  PENDING: '待执行',
  SUCCESS: '成功',
  FAILED: '失败'
}

export const EXECUTION_STATUS_TAG_TYPES = {
  PENDING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger'
}

export const OPERATOR_LABELS = {
  GT: '>',
  GTE: '≥',
  LT: '<',
  LTE: '≤',
  EQ: '=',
  NE: '≠'
}

export const SOURCE_TYPE_OPTIONS = [
  { value: 'PROPERTY', label: '属性上报' },
  { value: 'EVENT', label: '事件上报' }
]

export const ACTION_TYPE_OPTIONS = [
  { value: 'UPDATE_PROPERTY', label: '更新云端属性' },
  { value: 'SEND_COMMAND', label: '下发命令' },
  { value: 'FORWARD_MQTT', label: '转发 MQTT' },
  { value: 'FORWARD_HTTP', label: '转发 HTTP' }
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
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' }
]

/** 规则触发条件的中文摘要，按触发源拼装。 */
export function formatTriggerCondition(rule) {
  if (!rule) return '—'
  if (rule.sourceType === 'EVENT') {
    return rule.eventType ? `${rule.identifier ?? '—'}（${rule.eventType}）` : (rule.identifier ?? '—')
  }
  return `${rule.identifier ?? '—'} ${OPERATOR_LABELS[rule.operator] ?? rule.operator ?? ''} ${rule.thresholdValue ?? '—'}`
}

/** 规则动作的中文摘要，按动作类型读取 actionConfig。 */
export function formatActionSummary(rule) {
  if (!rule) return '—'
  const config = rule.actionConfig || {}
  switch (rule.actionType) {
    case 'UPDATE_PROPERTY':
      return `写入 ${config.identifier ?? '—'} = ${config.value ?? '—'}`
    case 'SEND_COMMAND':
      return `${config.commandType === 'service' ? '服务调用' : '属性设置'} → ${config.identifier ?? '—'}`
    case 'FORWARD_MQTT':
      return `MQTT → ${config.topic ?? '—'}`
    case 'FORWARD_HTTP':
      return `${(config.method || 'POST').toUpperCase()} ${config.url ?? '—'}`
    default:
      return ACTION_TYPE_LABELS[rule.actionType] || rule.actionType || '—'
  }
}

/** 时间戳格式化为本地时间，空值回退占位符。 */
export function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

/**
 * 规则表单的动作组装与作用域切档清理（T-29 从 RuleEditor 抽出）。
 *
 * 规则表单存的是**扁平动作字段**（`targetIdentifier` / `topic` / `url` …），没有 `actionConfig`。
 * 因此「扁平字段 → `actionConfig`」的组装必须唯一：提交体（`buildPayload`）与画布节点摘要
 * 共用这一份，否则摘要与提交体两处会漂移。
 */

/** 解析 JSON 对象文本；空文本返回 null，非法 JSON / 非对象抛错（与表单校验口径一致）。 */
function parseJsonObjectOrNull(text, label) {
  if (!text || !text.trim()) return null
  let parsed
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error(`${label}必须是合法 JSON`)
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${label}必须是 JSON 对象`)
  }
  return parsed
}

/** 扁平动作字段 → `actionConfig`；按 `form.actionType` 只读当前类型对应字段。 */
export function buildRuleActionConfig(form) {
  switch (form.actionType) {
    case 'UPDATE_PROPERTY':
      return { identifier: form.targetIdentifier.trim(), value: String(form.targetValue).trim() }
    case 'SEND_COMMAND': {
      const config = { commandType: form.commandType, identifier: form.targetIdentifier.trim() }
      const params = parseJsonObjectOrNull(form.commandParams, '命令参数')
      if (params) config.params = params
      return config
    }
    case 'FORWARD_MQTT': {
      const config = { topic: form.topic.trim(), qos: form.qos }
      if (form.payloadTemplate.trim()) config.payloadTemplate = form.payloadTemplate
      return config
    }
    default: {
      const config = { url: form.url.trim(), method: form.method }
      const headers = parseJsonObjectOrNull(form.headers, '请求头')
      if (headers) config.headers = headers
      if (form.payloadTemplate.trim()) config.payloadTemplate = form.payloadTemplate
      return config
    }
  }
}

/** 切档时清掉非当前档位的字段，避免提交体出现档位与字段不一致。 */
export function normalizeRuleScope(form) {
  if (form.scopeType !== 'DEVICE') form.deviceId = null
  if (form.scopeType !== 'GROUP') form.groupIds = []
  if (form.scopeType !== 'TAG') form.tagIds = []
}

/* ---------- 多条件 / 多动作（T-33 / G-6-7） ---------- */

export const CONDITION_LOGIC_LABELS = {
  AND: '全部满足（AND）',
  OR: '任一满足（OR）'
}

/** 条件数 / 动作数上限，与后端 RuleConstants 保持一致。 */
export const MAX_CONDITIONS = 10
export const MAX_ACTIONS = 5

/**
 * 空条件项。附加条件与动作都从它克隆，保证结构与首项完全一致。
 */
export function emptyCondition(sourceType = 'PROPERTY') {
  return {
    identifier: '',
    operator: 'GT',
    thresholdValue: '',
    eventType: '',
    sourceType
  }
}

/**
 * 空动作项。动作配置仍复用表单上的扁平字段（targetIdentifier / topic / url ...），
 * 这样附加动作与首个动作共用同一套 buildRuleActionConfig，配置约束自然一致。
 */
export function emptyAction(actionType = 'UPDATE_PROPERTY') {
  return {
    actionType,
    targetIdentifier: '',
    targetValue: '',
    commandType: 'property_set',
    commandParams: '',
    topic: '',
    qos: 1,
    url: '',
    method: 'POST',
    headers: '',
    payloadTemplate: ''
  }
}

/** 动作类型 → 中文文案（校验提示用）。 */
export function actionLabel(actionType) {
  return ACTION_TYPE_LABELS[actionType] || actionType || '未知动作'
}

/** 校验单个条件，返回错误文案；通过则返回空串。 */
export function validateCondition(condition, numericOperators, index) {
  const at = `第 ${index + 1} 个条件`
  if (!String(condition.identifier || '').trim()) return `${at}：请填写标识符`
  if (condition.sourceType !== 'EVENT') {
    const threshold = String(condition.thresholdValue || '').trim()
    if (!threshold) return `${at}：请填写阈值`
    if (numericOperators.has(condition.operator) && Number.isNaN(Number(threshold))) {
      return `${at}：数值比较符下阈值必须是数字`
    }
  }
  return ''
}

/** 校验单个动作，返回错误文案；通过则返回空串。复用与首个动作完全相同的约束。 */
export function validateAction(action) {
  switch (action.actionType) {
    case 'UPDATE_PROPERTY':
      if (!String(action.targetIdentifier || '').trim()) return '请填写目标属性标识符'
      if (!String(action.targetValue || '').trim()) return '请填写写入值'
      return ''
    case 'SEND_COMMAND':
      if (!String(action.targetIdentifier || '').trim()) return '请填写目标标识符'
      return ''
    case 'FORWARD_MQTT':
      if (!String(action.topic || '').trim()) return '请填写目标 Topic'
      if (action.topic.includes('#') || action.topic.includes('+')) {
        return 'Topic 不允许包含通配符 # / +'
      }
      return ''
    case 'FORWARD_HTTP':
      if (!String(action.url || '').trim()) return '请填写目标 URL'
      if (!/^https?:\/\//.test(String(action.url).trim())) {
        return 'URL 必须以 http:// 或 https:// 开头'
      }
      return ''
    default:
      return ''
  }
}
