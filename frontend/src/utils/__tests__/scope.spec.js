import { describe, expect, it } from 'vitest'
import { SCOPE_TYPE_OPTIONS, formatScopeLabel, resolveScopeType } from '../scope'

const CANDIDATES = {
  devices: [{ id: 9, deviceName: '1号温控器' }],
  groups: [
    { id: 1, name: '华东厂区' },
    { id: 2, name: '一号车间' }
  ],
  tags: [{ id: 7, name: '重点设备' }]
}

describe('utils/scope', () => {
  it('档位选项覆盖全部设备 / 指定设备 / 按分组 / 按标签四档', () => {
    expect(SCOPE_TYPE_OPTIONS.map((option) => option.value)).toEqual(['ALL', 'DEVICE', 'GROUP', 'TAG'])
  })

  it('resolveScopeType 优先取显式档位，缺省按 deviceId 推断', () => {
    expect(resolveScopeType({ scopeType: 'TAG', deviceId: 9 })).toBe('TAG')
    expect(resolveScopeType({ deviceId: 9 })).toBe('DEVICE')
    expect(resolveScopeType({ deviceId: null })).toBe('ALL')
    expect(resolveScopeType(null)).toBe('ALL')
  })

  it('formatScopeLabel 按档位渲染文案并解析候选名称', () => {
    expect(formatScopeLabel({ scopeType: 'ALL' }, CANDIDATES)).toBe('全部设备')
    expect(formatScopeLabel({ scopeType: 'DEVICE', deviceId: 9 }, CANDIDATES)).toBe('1号温控器')
    expect(formatScopeLabel({ scopeType: 'GROUP', groupIds: [1, 2] }, CANDIDATES)).toBe('分组：华东厂区、一号车间')
    expect(formatScopeLabel({ scopeType: 'TAG', tagIds: [7] }, CANDIDATES)).toBe('标签：重点设备')
  })

  it('formatScopeLabel 对缺省档位与未知候选降级', () => {
    expect(formatScopeLabel({ deviceId: 9 }, CANDIDATES)).toBe('1号温控器')
    expect(formatScopeLabel({ scopeType: 'DEVICE', deviceId: 99 }, CANDIDATES)).toBe('#99')
    expect(formatScopeLabel({ scopeType: 'DEVICE' }, CANDIDATES)).toBe('指定设备')
    expect(formatScopeLabel({ scopeType: 'GROUP', groupIds: [] }, CANDIDATES)).toBe('按分组')
    expect(formatScopeLabel({ scopeType: 'TAG', tagIds: [] }, CANDIDATES)).toBe('按标签')
    expect(formatScopeLabel(null, CANDIDATES)).toBe('—')
  })
})