/**
 * 规则与告警作用范围的展示映射与格式化（T-28）。
 *
 * 四档作用范围（全部设备 / 指定设备 / 按分组 / 按标签）的选项与文案集中在此，
 * 供告警规则与消息规则两处复用，避免各写一份出现口径漂移。
 * 分组 / 标签只落 ID，展示时按传入的候选列表实时解析名称。
 */

export const SCOPE_TYPE_LABELS = {
  ALL: '全部设备',
  DEVICE: '指定设备',
  GROUP: '按分组',
  TAG: '按标签'
}

export const SCOPE_TYPE_OPTIONS = [
  { value: 'ALL', label: '全部设备' },
  { value: 'DEVICE', label: '指定设备' },
  { value: 'GROUP', label: '按分组' },
  { value: 'TAG', label: '按标签' }
]

/** 档位为空时按 deviceId 是否为空推断，与后端缺省推断口径一致。 */
export function resolveScopeType(rule) {
  if (!rule) return 'ALL'
  if (rule.scopeType) return rule.scopeType
  return rule.deviceId ? 'DEVICE' : 'ALL'
}

function resolveName(list, id) {
  const matched = (list || []).find((item) => item.id === id)
  if (!matched) return `#${id}`
  return matched.name || matched.deviceName || matched.deviceKey || `#${id}`
}

/**
 * 作用范围的中文文案：`全部设备` / 设备名 / `分组：A、B` / `标签：X、Y`。
 *
 * @param {object} rule 规则或告警行，读取 scopeType / deviceId / groupIds / tagIds
 * @param {{devices?: Array, groups?: Array, tags?: Array}} candidates 候选列表，用于把 ID 解析为名称
 */
export function formatScopeLabel(rule, candidates = {}) {
  if (!rule) return '—'
  const { devices = [], groups = [], tags = [] } = candidates
  switch (resolveScopeType(rule)) {
    case 'DEVICE':
      return rule.deviceId ? resolveName(devices, rule.deviceId) : '指定设备'
    case 'GROUP': {
      const ids = rule.groupIds || []
      return ids.length ? `分组：${ids.map((id) => resolveName(groups, id)).join('、')}` : '按分组'
    }
    case 'TAG': {
      const ids = rule.tagIds || []
      return ids.length ? `标签：${ids.map((id) => resolveName(tags, id)).join('、')}` : '按标签'
    }
    default:
      return '全部设备'
  }
}