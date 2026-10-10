/**
 * 通知渠道与告警升级的展示映射与解析（T-27）。
 *
 * 渠道配置 / 事件 / 策略渠道 ID 在实体中均以 JSON 文本映射，前端需解析后再渲染；
 * 类型与事件文案集中在此，供渠道页、升级策略页与站内信中心复用，避免口径漂移。
 */

export const CHANNEL_TYPE_LABELS = {
  EMAIL: '邮件',
  SMS: '短信',
  INAPP: '站内信',
  WEBHOOK: 'Webhook'
}

export const CHANNEL_TYPE_OPTIONS = [
  { value: 'EMAIL', label: '邮件' },
  { value: 'SMS', label: '短信' }
]

export const EVENT_LABELS = {
  'alert.triggered': '告警触发',
  'alert.recovered': '告警恢复',
  'alert.escalated': '告警升级'
}

export const EVENT_OPTIONS = [
  { value: 'alert.triggered', label: '告警触发' },
  { value: 'alert.recovered', label: '告警恢复' },
  { value: 'alert.escalated', label: '告警升级' }
]

/** 升级策略适用级别：ALL 表示不限级别。 */
export const ESCALATION_SEVERITY_LABELS = {
  ALL: '全部级别',
  INFO: '提示',
  WARNING: '警告',
  CRITICAL: '严重'
}

export const ESCALATION_SEVERITY_OPTIONS = [
  { value: 'ALL', label: '全部级别' },
  { value: 'INFO', label: '提示' },
  { value: 'WARNING', label: '警告' },
  { value: 'CRITICAL', label: '严重' }
]

/** 解析后端返回的 JSON 文本列；非法或空值回退到默认值。 */
export function parseJson(text, fallback) {
  if (text === null || text === undefined || text === '') return fallback
  if (typeof text !== 'string') return text
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

/** 渠道事件数组格式化为中文文案。 */
export function formatEvents(events) {
  const list = parseJson(events, [])
  if (!Array.isArray(list) || list.length === 0) return '—'
  return list.map((event) => EVENT_LABELS[event] || event).join('、')
}