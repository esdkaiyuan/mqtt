import { describe, expect, it } from 'vitest'
import {
  ACTION_TYPE_LABELS,
  EXECUTION_STATUS_LABELS,
  SOURCE_TYPE_LABELS,
  formatActionSummary,
  formatDateTime,
  formatTriggerCondition
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