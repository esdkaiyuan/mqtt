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