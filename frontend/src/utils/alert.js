/**
 * 告警中心的展示映射与格式化（T-17）。
 *
 * 来源 / 级别 / 状态 / 比较符的文案与标签色集中在此，
 * 供告警列表、告警规则与顶栏通知三处复用，避免各写一份出现口径漂移。
 */

export const SOURCE_TYPE_LABELS = {
  THRESHOLD: '阈值',
  OFFLINE: '离线',
  EVENT: '事件'
}

export const SEVERITY_LABELS = {
  INFO: '提示',
  WARNING: '警告',
  CRITICAL: '严重'
}

export const SEVERITY_TAG_TYPES = {
  INFO: 'info',
  WARNING: 'warning',
  CRITICAL: 'danger'
}

export const STATUS_LABELS = {
  TRIGGERED: '待处理',
  ACKNOWLEDGED: '已确认',
  RECOVERED: '已恢复'
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
  { value: 'THRESHOLD', label: '阈值' },
  { value: 'OFFLINE', label: '离线' },
  { value: 'EVENT', label: '事件' }
]

export const SEVERITY_OPTIONS = [
  { value: 'INFO', label: '提示' },
  { value: 'WARNING', label: '警告' },
  { value: 'CRITICAL', label: '严重' }
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

export const STATUS_OPTIONS = [
  { value: 'TRIGGERED', label: '待处理' },
  { value: 'ACKNOWLEDGED', label: '已确认' },
  { value: 'RECOVERED', label: '已恢复' }
]

/** 规则触发条件的中文摘要，按来源类型拼装。 */
export function formatRuleCondition(rule) {
  if (!rule) return '—'
  switch (rule.sourceType) {
    case 'THRESHOLD':
      return `${rule.identifier ?? '—'} ${OPERATOR_LABELS[rule.operator] ?? rule.operator ?? ''} ${rule.thresholdValue ?? '—'}`
    case 'OFFLINE':
      return rule.offlineSeconds > 0 ? `离线超过 ${rule.offlineSeconds}s` : '变为离线即告警'
    case 'EVENT':
      return rule.eventType ? `${rule.identifier}（${rule.eventType}）` : (rule.identifier ?? '—')
    default:
      return '—'
  }
}

/** 时间戳格式化为本地时间，空值回退占位符。 */
export function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

/** 相对时间（供顶栏通知用），超过 30 天回退为日期。 */
export function formatRelativeTime(value) {
  if (!value) return '—'
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return '—'
  const diff = Date.now() - time
  if (diff < 0) return formatDateTime(value)
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 30 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / 86400000)} 天前`
  return new Date(value).toLocaleDateString('zh-CN')
}