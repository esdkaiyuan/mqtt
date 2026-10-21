import { describe, expect, it } from 'vitest'
import {
  createActionConfig,
  formatActionSummary,
  formatTriggerSummary,
  isFlowStep,
  isShadowTrigger,
  isStatusTrigger,
  normalizeParallelMembership,
  normalizeStep,
  normalizeTriggerSource,
  remapFlowRefsAfterMove,
  remapFlowRefsAfterRemove,
  validateParallelStructure
} from '../scene'

describe('utils/scene 上下线触发', () => {
  it('isStatusTrigger 只识别 ONLINE / OFFLINE', () => {
    expect(isStatusTrigger('ONLINE')).toBe(true)
    expect(isStatusTrigger('OFFLINE')).toBe(true)
    expect(isStatusTrigger('PROPERTY')).toBe(false)
    expect(isStatusTrigger('TIMER')).toBe(false)
  })

  it('formatTriggerSummary 返回设备上下线文案', () => {
    expect(formatTriggerSummary({ triggerType: 'ONLINE' })).toBe('设备上线')
    expect(formatTriggerSummary({ triggerType: 'OFFLINE' })).toBe('设备下线')
  })

  it('normalizeTriggerSource 清理状态源不支持的字段', () => {
    const form = {
      triggerType: 'ONLINE',
      triggerIdentifier: 'temperature',
      triggerOperator: 'GT',
      triggerThreshold: '40',
      thresholdEnabled: true,
      triggerEventType: 'alert',
      timerCron: '*/5 * * * *'
    }

    normalizeTriggerSource(form)

    expect(form).toMatchObject({
      triggerIdentifier: '',
      triggerOperator: null,
      triggerThreshold: '',
      thresholdEnabled: false,
      triggerEventType: '',
      timerCron: ''
    })
  })
})

describe('utils/scene 影子变更', () => {
  it('isShadowTrigger 只识别 SHADOW', () => {
    expect(isShadowTrigger('SHADOW')).toBe(true)
    expect(isShadowTrigger('PROPERTY')).toBe(false)
  })

  it('formatTriggerSummary 展示变更侧与可选标识符', () => {
    expect(formatTriggerSummary({ triggerType: 'SHADOW', triggerShadowSide: 'DESIRED' }))
      .toBe('影子变更（云端期望）')
    expect(formatTriggerSummary({
      triggerType: 'SHADOW',
      triggerShadowSide: 'REPORTED',
      triggerIdentifier: 'temperature'
    })).toBe('影子变更（设备上报） · temperature')
  })

  it('normalizeTriggerSource 保留标识符并设置默认变更侧', () => {
    const form = {
      triggerType: 'SHADOW',
      triggerIdentifier: 'temperature',
      triggerOperator: 'GT',
      triggerThreshold: '30',
      thresholdEnabled: true,
      triggerEventType: 'alert',
      timerCron: '*/5 * * * *'
    }

    normalizeTriggerSource(form)

    expect(form.triggerIdentifier).toBe('temperature')
    expect(form.triggerShadowSide).toBe('REPORTED')
    expect(form).toMatchObject({
      triggerOperator: null,
      triggerThreshold: '',
      thresholdEnabled: false,
      triggerEventType: '',
      timerCron: ''
    })
  })
})

describe('utils/scene 分支与循环', () => {
  it('isFlowStep 识别 BRANCH / LOOP / PARALLEL / JOIN', () => {
    expect(isFlowStep('BRANCH')).toBe(true)
    expect(isFlowStep('LOOP')).toBe(true)
    expect(isFlowStep('PARALLEL')).toBe(true)
    expect(isFlowStep('JOIN')).toBe(true)
    expect(isFlowStep('UPDATE_PROPERTY')).toBe(false)
  })

  it('createActionConfig 生成分支与循环缺省配置', () => {
    expect(createActionConfig('BRANCH')).toMatchObject({
      identifier: '',
      operator: 'GT',
      trueSeq: null,
      falseSeq: null
    })
    expect(createActionConfig('LOOP')).toEqual({ startSeq: null, times: 3 })
  })

  it('formatActionSummary 输出分支与循环摘要', () => {
    expect(formatActionSummary({
      actionType: 'BRANCH',
      actionConfig: { trueSeq: 4, falseSeq: null }
    })).toContain('真：第 4 步')
    expect(formatActionSummary({
      actionType: 'LOOP',
      actionConfig: { startSeq: 2, times: 3 }
    })).toContain('循环 3 次')
  })

  it('normalizeStep 为流程步骤清空目标，切回动作后恢复默认目标', () => {
    const flow = { actionType: 'BRANCH', targetType: 'TRIGGER', targetConfig: { deviceIds: [1] } }
    normalizeStep(flow)
    expect(flow.targetType).toBe('NONE')
    expect(flow.targetConfig).toBeNull()

    const action = { actionType: 'SEND_COMMAND', targetType: 'NONE', targetConfig: null }
    normalizeStep(action)
    expect(action.targetType).toBe('TRIGGER')
    expect(action.targetConfig).toEqual({ deviceIds: [], productIds: [], groupIds: [], tagIds: [] })
  })

  it('删除步骤后重映射分支与循环引用', () => {
    const steps = [
      { actionType: 'BRANCH', actionConfig: { trueSeq: 4, falseSeq: null } },
      { actionType: 'UPDATE_PROPERTY', actionConfig: {} },
      { actionType: 'LOOP', actionConfig: { startSeq: 1, times: 2 } },
      { actionType: 'UPDATE_PROPERTY', actionConfig: {} }
    ]

    remapFlowRefsAfterRemove(steps, 1)

    expect(steps[0].actionConfig).toEqual({ trueSeq: 3, falseSeq: null })
    expect(steps[2].actionConfig.startSeq).toBe(1)
  })

  it('移动步骤后重映射分支引用', () => {
    const steps = [
      { actionType: 'UPDATE_PROPERTY', actionConfig: {} },
      { actionType: 'BRANCH', actionConfig: { trueSeq: 4, falseSeq: null } },
      { actionType: 'UPDATE_PROPERTY', actionConfig: {} },
      { actionType: 'UPDATE_PROPERTY', actionConfig: {} }
    ]

    remapFlowRefsAfterMove(steps, 3, 2)

    expect(steps[1].actionConfig.trueSeq).toBe(3)
  })
})

describe('utils/scene 步骤并行', () => {
  function parallelSteps(overrides = {}) {
    return [
      { seq: 1, actionType: 'PARALLEL', actionConfig: { branches: 2 }, enabled: true },
      { seq: 2, actionType: 'UPDATE_PROPERTY', actionConfig: {}, branchIndex: 0, enabled: true },
      { seq: 3, actionType: 'UPDATE_PROPERTY', actionConfig: {}, branchIndex: 1, enabled: true },
      { seq: 4, actionType: 'JOIN', actionConfig: {}, enabled: true },
      { seq: 5, actionType: 'UPDATE_PROPERTY', actionConfig: {}, enabled: true },
      ...(overrides.tail ?? [])
    ]
  }

  it('createActionConfig 生成并行开始与汇合缺省配置', () => {
    expect(createActionConfig('PARALLEL')).toEqual({ branches: 2 })
    expect(createActionConfig('JOIN')).toEqual({})
  })

  it('formatActionSummary 输出并行与汇合摘要', () => {
    expect(formatActionSummary({ actionType: 'PARALLEL', actionConfig: { branches: 3 } }))
      .toBe('并行 3 路')
    expect(formatActionSummary({ actionType: 'JOIN', actionConfig: {} })).toBe('并行汇合')
  })

  it('normalizeParallelMembership 按块归属并清理块外字段', () => {
    const steps = parallelSteps()
    steps[1].branchIndex = null
    steps[3].parallelSeq = 1
    steps[4].parallelSeq = 1
    steps[4].branchIndex = 0

    normalizeParallelMembership(steps)

    expect(steps[0]).toMatchObject({ parallelSeq: null, branchIndex: null })
    expect(steps[1]).toMatchObject({ parallelSeq: 1, branchIndex: 0 })
    expect(steps[2]).toMatchObject({ parallelSeq: 1, branchIndex: 1 })
    expect(steps[3]).toMatchObject({ parallelSeq: null, branchIndex: null })
    expect(steps[4]).toMatchObject({ parallelSeq: null, branchIndex: null })
  })

  it('validateParallelStructure 接受成对并行块', () => {
    const steps = parallelSteps()
    normalizeParallelMembership(steps)

    expect(validateParallelStructure(steps)).toBe('')
  })

  it('validateParallelStructure 拒绝缺失汇合与空分支', () => {
    const noJoin = [
      { seq: 1, actionType: 'PARALLEL', actionConfig: { branches: 2 }, enabled: true },
      { seq: 2, actionType: 'UPDATE_PROPERTY', actionConfig: {}, branchIndex: 0, enabled: true }
    ]
    normalizeParallelMembership(noJoin)
    expect(validateParallelStructure(noJoin)).toContain('缺少对应的并行汇合')

    const emptyBranch = [
      { seq: 1, actionType: 'PARALLEL', actionConfig: { branches: 2 }, enabled: true },
      { seq: 2, actionType: 'UPDATE_PROPERTY', actionConfig: {}, branchIndex: 0, enabled: true },
      { seq: 3, actionType: 'JOIN', actionConfig: {}, enabled: true }
    ]
    normalizeParallelMembership(emptyBranch)
    expect(validateParallelStructure(emptyBranch)).toContain('分支 2 至少需要一个启用步骤')
  })

  it('validateParallelStructure 拒绝跳入并行块与循环体重叠', () => {
    const jump = [
      { seq: 1, actionType: 'BRANCH', actionConfig: { trueSeq: 3, falseSeq: null }, enabled: true },
      ...parallelSteps()
    ]
    normalizeParallelMembership(jump)
    expect(validateParallelStructure(jump)).toContain('分支不能跳入并行块')

    const loop = [
      { seq: 1, actionType: 'PARALLEL', actionConfig: { branches: 2 }, enabled: true },
      { seq: 2, actionType: 'UPDATE_PROPERTY', actionConfig: {}, branchIndex: 0, enabled: true },
      { seq: 3, actionType: 'UPDATE_PROPERTY', actionConfig: {}, branchIndex: 1, enabled: true },
      { seq: 4, actionType: 'JOIN', actionConfig: {}, enabled: true },
      { seq: 5, actionType: 'LOOP', actionConfig: { startSeq: 1, times: 2 }, enabled: true }
    ]
    normalizeParallelMembership(loop)
    expect(validateParallelStructure(loop)).toContain('循环体不能与')
  })
})
