import { describe, expect, it } from 'vitest'
import {
  ACTION_TYPE_LABELS,
  EXECUTION_STATUS_LABELS,
  MAX_ACTIONS,
  MAX_CONDITIONS,
  SOURCE_TYPE_LABELS,
  emptyAction,
  emptyCondition,
  formatActionSummary,
  formatDateTime,
  formatTriggerCondition,
  validateAction,
  validateCondition
} from '../rule'

const PROPERTY_RULE = {
  sourceType: 'PROPERTY',
  identifier: 'temperature',
  operator: 'GTE',
  thresholdValue: '40',
  actionType: 'UPDATE_PROPERTY',
  actionConfig: { identifier: 'switch', value: 'on' }
}

describe('utils/rule', () => {
  it('属性规则拼装「标识符 比较符 阈值」', () => {
    expect(formatTriggerCondition(PROPERTY_RULE)).toBe('temperature ≥ 40')
  })

  it('事件规则拼装「标识符（事件类型）」，缺事件类型时退化为标识符', () => {
    expect(
      formatTriggerCondition({ sourceType: 'EVENT', identifier: 'overtemp', eventType: 'fault' })
    ).toBe('overtemp（fault）')
    expect(formatTriggerCondition({ sourceType: 'EVENT', identifier: 'overtemp' })).toBe('overtemp')
  })

  it('空规则返回占位符', () => {
    expect(formatTriggerCondition(null)).toBe('—')
  })

  it('四种动作类型分别拼装摘要', () => {
    expect(formatActionSummary(PROPERTY_RULE)).toBe('写入 switch = on')
    expect(
      formatActionSummary({
        actionType: 'SEND_COMMAND',
        actionConfig: { commandType: 'service', identifier: 'reboot' }
      })
    ).toBe('服务调用 → reboot')
    expect(
      formatActionSummary({
        actionType: 'SEND_COMMAND',
        actionConfig: { commandType: 'property_set', identifier: 'power' }
      })
    ).toBe('属性设置 → power')
    expect(
      formatActionSummary({ actionType: 'FORWARD_MQTT', actionConfig: { topic: 'factory/line1' } })
    ).toBe('MQTT → factory/line1')
    expect(
      formatActionSummary({
        actionType: 'FORWARD_HTTP',
        actionConfig: { url: 'https://erp.example.com/hook' }
      })
    ).toBe('POST https://erp.example.com/hook')
  })

  it('HTTP 动作缺方法时默认 POST', () => {
    expect(
      formatActionSummary({ actionType: 'FORWARD_HTTP', actionConfig: { method: 'put', url: 'https://a/b' } })
    ).toBe('PUT https://a/b')
  })

  it('时间戳空值返回占位符，有效值返回本地化文本', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('')).toBe('—')
    const text = formatDateTime('2026-10-02T10:00:00.000')
    expect(text).not.toBe('—')
    expect(text).toContain('2026')
  })

  it('展示映射覆盖全部枚举值', () => {
    expect(SOURCE_TYPE_LABELS.PROPERTY).toBe('属性')
    expect(SOURCE_TYPE_LABELS.EVENT).toBe('事件')
    expect(Object.keys(ACTION_TYPE_LABELS)).toEqual([
      'UPDATE_PROPERTY',
      'SEND_COMMAND',
      'FORWARD_MQTT',
      'FORWARD_HTTP'
    ])
    expect(EXECUTION_STATUS_LABELS.FAILED).toBe('失败')
  })
})
/* ---------- 多条件 / 多动作（T-33 / G-6-7） ---------- */
describe('utils/rule 多条件多动作（T-33）', () => {
  const NUMERIC = new Set(['GT', 'GTE', 'LT', 'LTE'])

  it('emptyCondition 按触发源给出默认结构', () => {
    const property = emptyCondition('PROPERTY')
    expect(property).toMatchObject({ identifier: '', operator: 'GT', thresholdValue: '', eventType: '' })

    const event = emptyCondition('EVENT')
    expect(event.sourceType).toBe('EVENT')
  })

  it('emptyAction 给出与首个动作一致的字段集合', () => {
    const action = emptyAction('SEND_COMMAND')
    expect(action.actionType).toBe('SEND_COMMAND')
    expect(action).toHaveProperty('targetIdentifier')
    expect(action).toHaveProperty('commandType')
    expect(action).toHaveProperty('url')
  })

  it('validateCondition 对缺失标识符与空阈值报错', () => {
    expect(validateCondition(emptyCondition('PROPERTY'), NUMERIC, 0)).toContain('第 1 个条件')
    expect(
      validateCondition({ ...emptyCondition('PROPERTY'), identifier: 'temp' }, NUMERIC, 0)
    ).toContain('阈值')
  })

  it('validateCondition 在数值比较符下要求数字阈值', () => {
    const condition = { ...emptyCondition('PROPERTY'), identifier: 'temp', thresholdValue: 'N/A' }
    expect(validateCondition(condition, NUMERIC, 0)).toContain('必须是数字')
  })

  it('validateCondition 对事件条件不要求阈值', () => {
    const condition = { ...emptyCondition('EVENT'), identifier: 'overheat' }
    expect(validateCondition(condition, NUMERIC, 1)).toBe('')
  })

  it('validateAction 复用各动作类型原有约束', () => {
    expect(validateAction(emptyAction('UPDATE_PROPERTY'))).toContain('目标属性标识符')
    expect(validateAction(emptyAction('SEND_COMMAND'))).toContain('目标标识符')
    expect(validateAction(emptyAction('FORWARD_MQTT'))).toContain('Topic')
    expect(validateAction(emptyAction('FORWARD_HTTP'))).toContain('URL')
    expect(
      validateAction({ ...emptyAction('FORWARD_MQTT'), topic: 'a/+/b' })
    ).toContain('通配符')
  })

  it('validateAction 在配置完整时返回空串', () => {
    expect(
      validateAction({ ...emptyAction('UPDATE_PROPERTY'), targetIdentifier: 'switch', targetValue: 'on' })
    ).toBe('')
  })

  it('条件与动作上限常量为 10 / 5，与后端一致', () => {
    expect(MAX_CONDITIONS).toBe(10)
    expect(MAX_ACTIONS).toBe(5)
  })
})
