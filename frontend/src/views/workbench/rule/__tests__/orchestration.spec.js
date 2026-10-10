import { describe, expect, it } from 'vitest'
import { NODE_TYPE } from '@/utils/orchestration'
import { projectRule, applyRuleEvent } from '../orchestration'

function baseForm(overrides = {}) {
  return {
    sourceType: 'PROPERTY',
    identifier: 'temperature',
    operator: 'GT',
    thresholdValue: '40',
    eventType: '',
    scopeType: 'ALL',
    deviceId: null,
    groupIds: [],
    tagIds: [],
    actionType: 'UPDATE_PROPERTY',
    targetIdentifier: 'switch',
    targetValue: 'on',
    commandType: 'property_set',
    commandParams: '',
    topic: '',
    qos: 1,
    url: '',
    method: 'POST',
    headers: '',
    payloadTemplate: '',
    cooldownSeconds: 0,
    ...overrides
  }
}

function findEdge(graph, source, target) {
  return graph.edges.find((edge) => edge.source === source && edge.target === target)
}

describe('projectRule', () => {
  it('projects the trigger, scope, and action as a stable three-node chain', () => {
    const graph = projectRule(baseForm())

    expect(graph.nodes.map((node) => node.id)).toEqual([
      NODE_TYPE.TRIGGER,
      NODE_TYPE.SCOPE,
      NODE_TYPE.ACTION
    ])
    expect(graph.nodes.map((node) => node.data.key)).toEqual([
      NODE_TYPE.TRIGGER,
      NODE_TYPE.SCOPE,
      NODE_TYPE.ACTION
    ])
    expect(graph.edges.map(({ source, target }) => `${source}->${target}`)).toEqual([
      `${NODE_TYPE.TRIGGER}->${NODE_TYPE.SCOPE}`,
      `${NODE_TYPE.SCOPE}->${NODE_TYPE.ACTION}`
    ])
    expect(graph.nodes[0].data.summary).toBe('temperature > 40')
    expect(graph.nodes[1].data.summary).toBe('全部设备')
    expect(graph.nodes[2].data.summary).toBe('写入 switch = on')
  })

  it.each([
    ['ALL', '全部设备'],
    ['DEVICE', '设备-7'],
    ['GROUP', '分组：生产线'],
    ['TAG', '标签：关键']
  ])('formats the %s scope on the graph edge', (scopeType, expected) => {
    const graph = projectRule(baseForm({
      scopeType,
      deviceId: scopeType === 'DEVICE' ? 7 : null,
      groupIds: scopeType === 'GROUP' ? [3] : [],
      tagIds: scopeType === 'TAG' ? [5] : []
    }), {
      devices: [{ id: 7, deviceName: '设备-7' }],
      groups: [{ id: 3, name: '生产线' }],
      tags: [{ id: 5, name: '关键' }]
    })

    expect(findEdge(graph, NODE_TYPE.TRIGGER, NODE_TYPE.SCOPE).label).toBe(expected)
  })

  it('shows a cooldown edge label only when cooldownSeconds is positive', () => {
    const graph = projectRule(baseForm({ cooldownSeconds: 12 }))
    const noCooldown = projectRule(baseForm())

    expect(findEdge(graph, NODE_TYPE.SCOPE, NODE_TYPE.ACTION).label).toBe('冷却 12s')
    expect(findEdge(noCooldown, NODE_TYPE.SCOPE, NODE_TYPE.ACTION).label).toBe('')
  })

  it('formats event triggers from the identifier and event type', () => {
    const graph = projectRule(baseForm({
      sourceType: 'EVENT',
      identifier: 'overheat',
      eventType: 'fault'
    }))

    expect(graph.nodes[0].data.summary).toBe('overheat（fault）')
  })

  it('keeps the canvas renderable while an optional JSON field is being edited', () => {
    const graph = projectRule(baseForm({ actionType: 'SEND_COMMAND', commandParams: '{' }))

    expect(graph.nodes[2].data.summary).toBe('下发命令')
  })
})

describe('applyRuleEvent', () => {
  it('writes trigger, scope, and action node edits into the existing form', () => {
    const form = baseForm()

    expect(applyRuleEvent(form, {
      type: 'node-update',
      id: NODE_TYPE.TRIGGER,
      patch: { sourceType: 'EVENT', identifier: 'overheat', eventType: 'fault', unknown: 'ignored' }
    })).toBe(true)
    expect(applyRuleEvent(form, {
      type: 'node-update',
      id: NODE_TYPE.SCOPE,
      patch: { scopeType: 'GROUP', deviceId: 99, groupIds: [3], tagIds: [5] }
    })).toBe(true)
    expect(applyRuleEvent(form, {
      type: 'node-update',
      id: NODE_TYPE.ACTION,
      patch: { actionType: 'FORWARD_MQTT', topic: 'site/events', qos: 0, targetIdentifier: 'switch' }
    })).toBe(true)

    expect(form).toMatchObject({
      sourceType: 'EVENT',
      identifier: 'overheat',
      eventType: 'fault',
      scopeType: 'GROUP',
      deviceId: null,
      groupIds: [3],
      tagIds: [],
      actionType: 'FORWARD_MQTT',
      topic: 'site/events',
      qos: 0,
      targetIdentifier: 'switch'
    })
    expect(form.unknown).toBeUndefined()
  })

  it('keeps inactive action fields when switching action type', () => {
    const form = baseForm({ targetIdentifier: 'switch', url: 'https://example.test/hook' })

    applyRuleEvent(form, {
      type: 'node-update',
      id: NODE_TYPE.ACTION,
      patch: { actionType: 'FORWARD_HTTP' }
    })

    expect(form.targetIdentifier).toBe('switch')
    expect(form.url).toBe('https://example.test/hook')
  })

  it('does not allow structural edits to the fixed rule graph', () => {
    const form = baseForm()

    expect(applyRuleEvent(form, { type: 'node-add', nodeType: NODE_TYPE.ACTION })).toBe(false)
    expect(applyRuleEvent(form, { type: 'node-remove', id: NODE_TYPE.ACTION })).toBe(false)
    expect(applyRuleEvent(form, { type: 'node-reorder', id: NODE_TYPE.ACTION })).toBe(false)
    expect(applyRuleEvent(form, { type: 'node-update', id: 'missing', patch: {} })).toBe(false)
    expect(applyRuleEvent(form, null)).toBe(false)
  })
})
