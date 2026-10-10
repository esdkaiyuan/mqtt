import { describe, expect, it } from 'vitest'
import { projectScene, applySceneEvent } from '../orchestration'
import { NODE_TYPE, MAX_CONDITIONS, MAX_STEPS } from '@/utils/orchestration'
import { createStep, withNormalizedSeq } from '@/utils/scene'

/** 与 SceneFormDialog.emptyForm() 同形的场景表单快照。 */
function baseForm(overrides = {}) {
  return {
    triggerType: 'PROPERTY',
    triggerDeviceId: null,
    triggerIdentifier: 'temperature',
    triggerOperator: 'GT',
    triggerThreshold: '30',
    thresholdEnabled: true,
    triggerEventType: '',
    timerCron: '',
    conditionLogic: 'AND',
    conditions: [],
    cooldownSeconds: 0,
    enabled: true,
    steps: [],
    ...overrides
  }
}

function nodeIds({ nodes }) {
  return nodes.map((node) => node.id)
}

function edgePairs({ edges }) {
  return edges.map((edge) => `${edge.source}->${edge.target}`)
}

function findEdge({ edges }, source, target) {
  return edges.find((edge) => edge.source === source && edge.target === target)
}

describe('projectScene', () => {
  it('无条件项时：触发直接连到步骤 1，不生成条件组节点', () => {
    const form = baseForm({ steps: withNormalizedSeq([createStep()]) })
    const graph = projectScene(form)

    expect(nodeIds(graph)).toEqual([NODE_TYPE.TRIGGER, 'step-0'])
    expect(edgePairs(graph)).toEqual([`${NODE_TYPE.TRIGGER}->step-0`])
  })

  it('有条件项时：条件节点汇聚条件组，再连到步骤 1', () => {
    const form = baseForm({
      conditions: [
        { deviceId: null, identifier: 'temperature', operator: 'GT', threshold: '30' },
        { deviceId: 9, identifier: 'humidity', operator: 'LT', threshold: '20' }
      ],
      steps: withNormalizedSeq([createStep(), createStep()])
    })
    const graph = projectScene(form)

    expect(nodeIds(graph)).toEqual([
      NODE_TYPE.TRIGGER,
      'condition-0',
      'condition-1',
      NODE_TYPE.CONDITION_GROUP,
      'step-0',
      'step-1'
    ])
    expect(edgePairs(graph)).toEqual([
      'condition-0->conditionGroup',
      'condition-1->conditionGroup',
      `${NODE_TYPE.TRIGGER}->conditionGroup`,
      'conditionGroup->step-0',
      'step-0->step-1'
    ])
  })

  it('步骤按 seq 顺序成链，边标签只在延时大于 0 时出现', () => {
    const steps = withNormalizedSeq([
      createStep(),
      { ...createStep(), delaySeconds: 5 },
      { ...createStep(), delaySeconds: 0 }
    ])
    const graph = projectScene(baseForm({ steps }))

    expect(findEdge(graph, 'step-0', 'step-1').label).toBe('延时 5s')
    expect(findEdge(graph, 'step-1', 'step-2').label).toBe('')
  })

  it('节点 key 使用稳定标识，步骤节点标记可重排、条件与步骤可删除', () => {
    const form = baseForm({
      conditions: [{ deviceId: null, identifier: 'a', operator: 'GT', threshold: '1' }],
      steps: withNormalizedSeq([createStep()])
    })
    const { nodes } = projectScene(form)

    expect(nodes.map((node) => node.data.key)).toEqual([
      'trigger',
      'condition-0',
      'conditionGroup',
      'step-0'
    ])
    expect(nodes.find((node) => node.id === 'step-0').data.reorderable).toBe(true)
    expect(nodes.find((node) => node.id === 'step-0').data.removable).toBe(true)
    expect(nodes.find((node) => node.id === 'condition-0').data.removable).toBe(true)
    expect(nodes.find((node) => node.id === 'trigger').data.removable).toBeFalsy()
  })

  it('触发节点摘要按 triggerType 分支', () => {
    const property = projectScene(baseForm()).nodes[0]
    expect(property.data.summary).toBe('temperature > 30')

    const event = projectScene(
      baseForm({ triggerType: 'EVENT', triggerIdentifier: 'door_open', triggerEventType: 'alert' })
    ).nodes[0]
    expect(event.data.summary).toBe('door_open（alert）')

    const timer = projectScene(
      baseForm({ triggerType: 'TIMER', timerCron: '*/5 * * * *' })
    ).nodes[0]
    expect(timer.data.summary).toBe('定时 */5 * * * *')
  })

  it('条件组摘要取 conditionLogic 文案，条件摘要复用 formatCondition', () => {
    const form = baseForm({
      conditionLogic: 'OR',
      conditions: [{ deviceId: null, identifier: 'temperature', operator: 'GT', threshold: '30' }]
    })
    const { nodes } = projectScene(form)

    expect(nodes.find((node) => node.id === 'conditionGroup').data.summary).toBe('满足任一条件')
    expect(nodes.find((node) => node.id === 'condition-0').data.summary).toBe('temperature > 30')
  })

  it('对非数组 conditions / steps 与 null 项降级，不抛异常', () => {
    const graph = projectScene(baseForm({ conditions: null, steps: [null, createStep()] }))

    expect(nodeIds(graph)).toEqual([NODE_TYPE.TRIGGER, 'step-0'])
    expect(graph.edges).toHaveLength(1)
  })
})

describe('applySceneEvent', () => {
  it('node-add 条件：追加 createCondition 形状', () => {
    const form = baseForm()
    expect(applySceneEvent(form, { type: 'node-add', nodeType: NODE_TYPE.CONDITION })).toBe(true)
    expect(form.conditions).toEqual([
      { deviceId: null, identifier: '', operator: 'GT', threshold: '' }
    ])
  })

  it('node-add 步骤：追加并归一化 seq', () => {
    const form = baseForm({ steps: withNormalizedSeq([createStep()]) })
    expect(applySceneEvent(form, { type: 'node-add', nodeType: NODE_TYPE.STEP })).toBe(true)
    expect(form.steps.map((step) => step.seq)).toEqual([1, 2])
  })

  it('node-add 步骤：TIMER 触发源下新步骤目标锁 FIXED', () => {
    const form = baseForm({ triggerType: 'TIMER' })
    applySceneEvent(form, { type: 'node-add', nodeType: NODE_TYPE.STEP })
    expect(form.steps[0].targetType).toBe('FIXED')
  })

  it('node-add 达到上限时拒绝且不改动 form', () => {
    const full = baseForm({ conditions: Array.from({ length: MAX_CONDITIONS }, () => ({ identifier: 'a' })) })
    expect(applySceneEvent(full, { type: 'node-add', nodeType: NODE_TYPE.CONDITION })).toBe(false)
    expect(full.conditions).toHaveLength(MAX_CONDITIONS)

    const manySteps = baseForm({ steps: withNormalizedSeq(Array.from({ length: MAX_STEPS }, () => createStep())) })
    expect(applySceneEvent(manySteps, { type: 'node-add', nodeType: NODE_TYPE.STEP })).toBe(false)
    expect(manySteps.steps).toHaveLength(MAX_STEPS)
  })

  it('node-remove 条件：按索引移除', () => {
    const form = baseForm({
      conditions: [
        { identifier: 'a' },
        { identifier: 'b' },
        { identifier: 'c' }
      ]
    })
    expect(applySceneEvent(form, { type: 'node-remove', id: 'condition-1' })).toBe(true)
    expect(form.conditions.map((item) => item.identifier)).toEqual(['a', 'c'])
  })

  it('node-remove 步骤：移除后 seq 归一化为 1..N', () => {
    const form = baseForm({
      steps: withNormalizedSeq([createStep(), createStep(), createStep()])
    })
    expect(applySceneEvent(form, { type: 'node-remove', id: 'step-0' })).toBe(true)
    expect(form.steps).toHaveLength(2)
    expect(form.steps.map((step) => step.seq)).toEqual([1, 2])
  })

  it('node-remove 越界或未知 id 时为无操作', () => {
    const form = baseForm({ conditions: [{ identifier: 'a' }] })
    expect(applySceneEvent(form, { type: 'node-remove', id: 'condition-5' })).toBe(false)
    expect(applySceneEvent(form, { type: 'node-remove', id: NODE_TYPE.TRIGGER })).toBe(false)
    expect(form.conditions).toHaveLength(1)
  })

  it('node-reorder：按落点位置换算新顺序并归一化 seq', () => {
    const form = baseForm({
      steps: withNormalizedSeq([createStep(), createStep(), createStep()])
    })
    form.steps[0].delaySeconds = 1
    form.steps[1].delaySeconds = 2
    form.steps[2].delaySeconds = 3
    const targetPosition = projectScene(form).nodes.find((node) => node.id === 'step-2').position

    expect(
      applySceneEvent(form, { type: 'node-reorder', id: 'step-0', position: targetPosition })
    ).toBe(true)
    expect(form.steps.map((step) => step.delaySeconds)).toEqual([2, 3, 1])
    expect(form.steps.map((step) => step.seq)).toEqual([1, 2, 3])
  })

  it('node-reorder 落在原位时为无操作', () => {
    const form = baseForm({ steps: withNormalizedSeq([createStep(), createStep()]) })
    const position = projectScene(form).nodes.find((node) => node.id === 'step-0').position
    expect(applySceneEvent(form, { type: 'node-reorder', id: 'step-0', position })).toBe(false)
  })

  it('node-update 触发节点：仅回写触发字段', () => {
    const form = baseForm()
    expect(
      applySceneEvent(form, {
        type: 'node-update',
        id: NODE_TYPE.TRIGGER,
        patch: { triggerIdentifier: 'humidity', unknownField: 'x' }
      })
    ).toBe(true)
    expect(form.triggerIdentifier).toBe('humidity')
    expect(form.unknownField).toBeUndefined()
  })

  it('node-update 条件组：回写 conditionLogic', () => {
    const form = baseForm()
    applySceneEvent(form, {
      type: 'node-update',
      id: NODE_TYPE.CONDITION_GROUP,
      patch: { conditionLogic: 'OR' }
    })
    expect(form.conditionLogic).toBe('OR')
  })

  it('node-update 条件节点：回写条件字段', () => {
    const form = baseForm({ conditions: [{ deviceId: null, identifier: 'a', operator: 'GT', threshold: '1' }] })
    applySceneEvent(form, {
      type: 'node-update',
      id: 'condition-0',
      patch: { identifier: 'b', threshold: '9' }
    })
    expect(form.conditions[0]).toMatchObject({ identifier: 'b', threshold: '9', operator: 'GT' })
  })

  it('node-update 步骤节点：回写字段并按动作类型重建 actionConfig', () => {
    const form = baseForm({ steps: withNormalizedSeq([createStep()]) })
    applySceneEvent(form, {
      type: 'node-update',
      id: 'step-0',
      patch: { delaySeconds: 10, actionType: 'FORWARD_MQTT' }
    })

    const step = form.steps[0]
    expect(step.delaySeconds).toBe(10)
    expect(step.actionType).toBe('FORWARD_MQTT')
    expect(step.actionConfig).toEqual({ topic: '', qos: 1, payloadTemplate: '' })
    expect(step.targetType).toBe('TRIGGER')
    expect(step.targetConfig).toBeNull()
  })

  it('node-update 步骤节点：动作类型未变化时不重建 actionConfig', () => {
    const form = baseForm({ steps: withNormalizedSeq([createStep()]) })
    form.steps[0].actionConfig = { identifier: 'power', value: 'on' }
    applySceneEvent(form, {
      type: 'node-update',
      id: 'step-0',
      patch: { actionType: 'UPDATE_PROPERTY', delaySeconds: 3 }
    })
    expect(form.steps[0].actionConfig).toEqual({ identifier: 'power', value: 'on' })
  })

  it('未知事件或缺失参数时为无操作', () => {
    const form = baseForm()
    expect(applySceneEvent(form, { type: 'node-move' })).toBe(false)
    expect(applySceneEvent(form, { type: 'node-update', id: 'step-9', patch: {} })).toBe(false)
    expect(applySceneEvent(form, null)).toBe(false)
  })
})