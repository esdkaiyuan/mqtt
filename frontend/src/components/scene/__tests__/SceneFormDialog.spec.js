import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, focusErrorMock, hooks } = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  focusErrorMock: vi.fn(() => true),
  hooks: { saving: null, createScene: null, updateScene: null }
}))

vi.mock('element-plus', () => ({ ElMessage: messageMock }))

vi.mock('@tanstack/vue-query', async () => {
  const { ref } = await import('vue')
  return { useQuery: () => ({ data: ref([]), refetch: vi.fn() }) }
})

vi.mock('@/composables/useScenes', async () => {
  const { ref } = await import('vue')
  hooks.saving = ref(false)
  hooks.createScene = vi.fn(() => Promise.resolve())
  hooks.updateScene = vi.fn(() => Promise.resolve())
  return {
    useSceneMutations: () => ({
      saving: hooks.saving,
      createScene: hooks.createScene,
      updateScene: hooks.updateScene
    })
  }
})

vi.mock('@/composables/useDeviceGroups', async () => {
  const { ref } = await import('vue')
  return {
    useDeviceGroupTreeQuery: () => ({ tree: ref([]) }),
    useTagListQuery: () => ({ tags: ref([]) })
  }
})

vi.mock('@/api/device', () => ({
  deviceApi: { getList: vi.fn(() => Promise.resolve({ code: 200, data: { records: [] } })) }
}))

vi.mock('@/api/product', () => ({
  productApi: { getList: vi.fn(() => Promise.resolve({ code: 200, data: [] })) }
}))

import SceneFormDialog from '../SceneFormDialog.vue'

const VALID_SCENE = {
  id: 7,
  name: '高温联动',
  description: '温度过高时开风扇',
  triggerType: 'PROPERTY',
  triggerIdentifier: 'temperature',
  triggerOperator: 'GT',
  triggerThreshold: '40',
  conditions: [],
  cooldownSeconds: 60,
  enabled: 1,
  steps: [
    {
      seq: 1,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetConfig: { deviceIds: [], productIds: [], groupIds: [], tagIds: [] },
      actionConfig: { identifier: 'switch', value: 'on' },
      enabled: true
    }
  ]
}

const BAD_SCENE = {
  ...VALID_SCENE,
  steps: [{ ...VALID_SCENE.steps[0], actionConfig: { identifier: '', value: '' } }]
}

const FLOW_SCENE = {
  ...VALID_SCENE,
  id: 8,
  name: '流程场景',
  steps: [
    {
      seq: 1,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetConfig: null,
      actionConfig: { identifier: 'switch', value: 'on' },
      enabled: true
    },
    {
      seq: 2,
      delaySeconds: 0,
      actionType: 'LOOP',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: { startSeq: 1, times: 2 },
      enabled: true
    },
    {
      seq: 3,
      delaySeconds: 0,
      actionType: 'BRANCH',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: {
        deviceId: null,
        identifier: 'temperature',
        operator: 'GT',
        threshold: '30',
        trueSeq: 4,
        falseSeq: null
      },
      enabled: true
    },
    {
      seq: 4,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetConfig: null,
      actionConfig: { identifier: 'switch', value: 'off' },
      enabled: true
    }
  ]
}

const PARALLEL_SCENE = {
  ...VALID_SCENE,
  id: 10,
  name: '并行场景',
  steps: [
    {
      seq: 1,
      delaySeconds: 0,
      actionType: 'PARALLEL',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: { branches: 2 },
      enabled: true
    },
    {
      seq: 2,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetConfig: null,
      actionConfig: { identifier: 'switch', value: 'on' },
      parallelSeq: 1,
      branchIndex: 0,
      enabled: true
    },
    {
      seq: 3,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetConfig: null,
      actionConfig: { identifier: 'switch', value: 'off' },
      parallelSeq: 1,
      branchIndex: 1,
      enabled: true
    },
    {
      seq: 4,
      delaySeconds: 0,
      actionType: 'JOIN',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: {},
      enabled: true
    },
    {
      seq: 5,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetConfig: null,
      actionConfig: { identifier: 'switch', value: 'off' },
      enabled: true
    }
  ]
}

const INVALID_PARALLEL_SCENE = {
  ...PARALLEL_SCENE,
  steps: PARALLEL_SCENE.steps.filter((step) => step.actionType !== 'JOIN')
}

const INVALID_BRANCH_SCENE = {
  ...FLOW_SCENE,
  steps: [
    {
      seq: 1,
      delaySeconds: 0,
      actionType: 'BRANCH',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: {
        deviceId: null,
        identifier: 'temperature',
        operator: 'GT',
        threshold: '30',
        trueSeq: null,
        falseSeq: null
      },
      enabled: true
    },
    {
      seq: 2,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetConfig: null,
      actionConfig: { identifier: 'switch', value: 'on' },
      enabled: true
    }
  ]
}

const SHADOW_SCENE = {
  ...VALID_SCENE,
  id: 9,
  name: '影子联动',
  triggerType: 'SHADOW',
  triggerIdentifier: '',
  triggerOperator: null,
  triggerThreshold: null,
  triggerShadowSide: 'ANY'
}

/* ---------- Element Plus 最小替身 ---------- */
const ElDrawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'size'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-drawer', 'data-size': props.size }, [
            h('div', { class: 'stub-drawer-header' }, slots.header?.()),
            h('div', { class: 'stub-drawer-body' }, slots.default?.()),
            h('div', { class: 'stub-drawer-footer' }, slots.footer?.())
          ])
        : null
  }
}

const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue', 'placeholder', 'maxlength', 'clearable', 'filterable'],
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        class: 'stub-input',
        value: props.modelValue ?? '',
        placeholder: props.placeholder,
        onInput: (event) => emit('update:modelValue', event.target.value)
      })
  }
}

const ElInputNumberStub = {
  name: 'ElInputNumber',
  props: ['modelValue', 'min', 'max'],
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        class: 'stub-number',
        value: props.modelValue,
        onInput: (event) => emit('update:modelValue', Number(event.target.value))
      })
  }
}

const ElSwitchStub = {
  name: 'ElSwitch',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    return () =>
      h(
        'button',
        { class: 'stub-switch', onClick: () => emit('update:modelValue', !props.modelValue) },
        String(props.modelValue)
      )
  }
}

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'clearable', 'filterable', 'multiple', 'disabled'],
  emits: ['update:modelValue', 'change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-select' }, slots.default?.())
  }
}

const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  setup(props) {
    return () => h('div', { class: 'stub-option' }, props.label)
  }
}

const ElRadioGroupStub = {
  name: 'ElRadioGroup',
  props: ['modelValue', 'size'],
  emits: ['update:modelValue', 'change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-radio-group' }, slots.default?.())
  }
}

const ElRadioButtonStub = {
  name: 'ElRadioButton',
  props: ['value'],
  setup(_, { slots }) {
    return () => h('label', { class: 'stub-radio' }, slots.default?.())
  }
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['disabled', 'loading'],
  emits: ['click'],
  setup(_, { slots, emit }) {
    return () => h('button', { class: 'stub-btn', onClick: () => emit('click') }, slots.default?.())
  }
}

const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'stub-form-item' }, [
        props.label ? h('span', { class: 'stub-form-label' }, props.label) : null,
        slots.default?.()
      ])
  }
}

const passthrough = (name) => ({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `stub-${name}` }, slots.default?.())
  }
})

/** 画布视图替身：暴露 focusError 以验证校验错误定位委托（设计文档 §10.3）。 */
const SceneOrchestrationViewStub = {
  name: 'SceneOrchestrationView',
  props: ['form', 'devices', 'products', 'groupTree', 'tags'],
  setup(_, { expose }) {
    expose({ focusError: focusErrorMock })
    return () => h('div', { class: 'stub-scene-orch' })
  }
}

const STUBS = {
  ElDrawer: ElDrawerStub,
  ElForm: passthrough('ElForm'),
  ElFormItem: ElFormItemStub,
  ElInput: ElInputStub,
  ElInputNumber: ElInputNumberStub,
  ElSwitch: ElSwitchStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElRadioGroup: ElRadioGroupStub,
  ElRadioButton: ElRadioButtonStub,
  ElButton: ElButtonStub,
  ElTreeSelect: passthrough('ElTreeSelect'),
  SvgIcon: true,
  SceneConditionEditor: passthrough('SceneConditionEditor'),
  SceneStepEditor: passthrough('SceneStepEditor'),
  SceneTestDialog: { name: 'SceneTestDialog', props: ['modelValue'], template: '<div class="stub-test-dialog" />' },
  SceneOrchestrationView: SceneOrchestrationViewStub
}

function mountDialog(scene = null) {
  return mount(SceneFormDialog, { props: { modelValue: true, scene }, global: { stubs: STUBS } })
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

/** 头部「表单 / 画布」分段控件（与触发判定单选组同类型，靠「画布」文案区分）。 */
function viewToggle(wrapper) {
  return wrapper.findAllComponents({ name: 'ElRadioGroup' }).find((group) => group.text().includes('画布'))
}

async function switchView(wrapper, mode) {
  viewToggle(wrapper).vm.$emit('update:modelValue', mode)
  await nextTick()
}

/** 触发方式下拉（选项含「定时触发」，全抽屉唯一）。 */
function triggerTypeSelect(wrapper) {
  return wrapper.findAllComponents({ name: 'ElSelect' }).find((select) => select.text().includes('定时触发'))
}

async function changeTriggerType(wrapper, value) {
  const select = triggerTypeSelect(wrapper)
  select.vm.$emit('update:modelValue', value)
  select.vm.$emit('change', value)
  await nextTick()
}

describe('components/scene/SceneFormDialog', () => {
  let wrapper

  beforeEach(() => {
    focusErrorMock.mockClear()
    focusErrorMock.mockReturnValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('默认表单视图：头部承载全局属性，主体渲染表单子编辑器', async () => {
    wrapper = mountDialog()
    await flushPromises()

    const drawer = wrapper.find('.stub-drawer')
    expect(drawer.exists()).toBe(true)
    expect(drawer.attributes('data-size')).toBe('640px')

    // 全局属性在头部（非节点），两视图共用
    const header = wrapper.find('.stub-drawer-header')
    expect(header.text()).toContain('场景名称')
    expect(header.text()).toContain('冷却窗口（秒）')
    expect(header.find('input[placeholder="如：回家开灯"]').exists()).toBe(true)

    expect(viewToggle(wrapper).exists()).toBe(true)
    expect(wrapper.find('.stub-SceneStepEditor').exists()).toBe(true)
    expect(wrapper.find('.stub-SceneConditionEditor').exists()).toBe(true)
    expect(wrapper.find('.stub-scene-orch').exists()).toBe(false)
  })

  it('切到画布视图渲染画布并放宽抽屉宽度，切换不重置 form', async () => {
    wrapper = mountDialog(VALID_SCENE)
    await flushPromises()

    await wrapper.find('input[placeholder="如：回家开灯"]').setValue('改名场景')

    await switchView(wrapper, 'canvas')

    expect(wrapper.find('.stub-scene-orch').exists()).toBe(true)
    expect(wrapper.find('.stub-SceneStepEditor').exists()).toBe(false)
    expect(wrapper.find('.stub-drawer').attributes('data-size')).toBe('min(1100px, 92vw)')
    // 画布视图收到同一份 form，且切换未重置已编辑字段
    expect(wrapper.findComponent({ name: 'SceneOrchestrationView' }).props('form').name).toBe('改名场景')

    await switchView(wrapper, 'form')
    expect(wrapper.find('input[placeholder="如：回家开灯"]').element.value).toBe('改名场景')
  })

  it('画布视图下头部全局属性可编辑并进入提交体', async () => {
    wrapper = mountDialog(VALID_SCENE)
    await flushPromises()

    await switchView(wrapper, 'canvas')
    await wrapper.find('input[placeholder="如：回家开灯"]').setValue('画布改名')

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(hooks.updateScene).toHaveBeenCalledTimes(1)
    expect(hooks.updateScene.mock.calls[0][0]).toBe(7)
    expect(hooks.updateScene.mock.calls[0][1].name).toBe('画布改名')
  })

  it('表单视图与画布视图产出的提交体逐字段一致', async () => {
    const formWrapper = mountDialog(VALID_SCENE)
    await flushPromises()
    await buttonByText(formWrapper, '保存').trigger('click')
    await flushPromises()
    const formPayload = hooks.updateScene.mock.calls[0][1]
    formWrapper.unmount()

    hooks.updateScene.mockClear()

    const canvasWrapper = mountDialog(VALID_SCENE)
    await flushPromises()
    await switchView(canvasWrapper, 'canvas')
    await buttonByText(canvasWrapper, '保存').trigger('click')
    await flushPromises()
    const canvasPayload = hooks.updateScene.mock.calls[0][1]
    canvasWrapper.unmount()

    expect(canvasPayload).toEqual(formPayload)
  })

  it('画布视图下校验失败时提示并把错误定位到节点', async () => {
    wrapper = mountDialog(BAD_SCENE)
    await flushPromises()

    await switchView(wrapper, 'canvas')
    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(hooks.updateScene).not.toHaveBeenCalled()
    expect(messageMock.warning).toHaveBeenCalledWith('第 1 步：请填写目标属性标识符')
    expect(focusErrorMock).toHaveBeenCalledWith('第 1 步：请填写目标属性标识符')
  })

  it('表单视图下校验失败不触发画布定位', async () => {
    wrapper = mountDialog(BAD_SCENE)
    await flushPromises()

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(messageMock.warning).toHaveBeenCalledWith('第 1 步：请填写目标属性标识符')
    expect(focusErrorMock).not.toHaveBeenCalled()
  })

  it('切换触发方式清理源专属字段（与画布触发面板共用口径）', async () => {
    wrapper = mountDialog(VALID_SCENE)
    await flushPromises()

    expect(wrapper.find('input[placeholder="如：temperature"]').element.value).toBe('temperature')

    await changeTriggerType(wrapper, 'TIMER')
    expect(wrapper.find('input[placeholder="如：*/5 * * * *"]').exists()).toBe(true)

    await changeTriggerType(wrapper, 'PROPERTY')
    // 切到 TIMER 时清空了属性标识符，切回后应保持为空
    expect(wrapper.find('input[placeholder="如：temperature"]').element.value).toBe('')
  })

  it('上下线触发隐藏标识符并提交空触发标识', async () => {
    wrapper = mountDialog(VALID_SCENE)
    await flushPromises()

    await changeTriggerType(wrapper, 'ONLINE')

    expect(wrapper.find('.form-hint--block').text()).toContain('设备状态变化时触发')
    expect(wrapper.find('input[placeholder="如：temperature"]').exists()).toBe(false)

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    const payload = hooks.updateScene.mock.calls[0][1]
    expect(payload.triggerType).toBe('ONLINE')
    expect(payload.triggerIdentifier).toBeNull()
    expect(payload.triggerOperator).toBeNull()
    expect(payload.triggerThreshold).toBeNull()
    expect(payload.timerCron).toBeNull()
  })

  it('上下线触发切回属性触发后标识符保持清空', async () => {
    wrapper = mountDialog(VALID_SCENE)
    await flushPromises()

    await changeTriggerType(wrapper, 'OFFLINE')
    await changeTriggerType(wrapper, 'PROPERTY')

    expect(wrapper.find('input[placeholder="如：temperature"]').element.value).toBe('')
  })

  it('分支步骤缺少跳转目标时阻止提交', async () => {
    wrapper = mountDialog(INVALID_BRANCH_SCENE)
    await flushPromises()

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(hooks.updateScene).not.toHaveBeenCalled()
    expect(messageMock.warning).toHaveBeenCalledWith('第 1 步：请至少设置一个分支跳转目标')
  })

  it('分支与循环步骤提交 NONE 目标并保留流程配置', async () => {
    wrapper = mountDialog(FLOW_SCENE)
    await flushPromises()

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    const payload = hooks.updateScene.mock.calls[0][1]
    expect(payload.steps[1]).toMatchObject({
      actionType: 'LOOP',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: { startSeq: 1, times: 2 }
    })
    expect(payload.steps[2]).toMatchObject({
      actionType: 'BRANCH',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: { trueSeq: 4, falseSeq: null }
    })
  })

  it('并行步骤提交 NONE 目标并携带分支归属', async () => {
    wrapper = mountDialog(PARALLEL_SCENE)
    await flushPromises()

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    const payload = hooks.updateScene.mock.calls[0][1]
    expect(payload.steps[0]).toMatchObject({
      actionType: 'PARALLEL',
      targetType: 'NONE',
      targetConfig: null,
      actionConfig: { branches: 2 },
      parallelSeq: null,
      branchIndex: null
    })
    expect(payload.steps[1]).toMatchObject({ parallelSeq: 1, branchIndex: 0 })
    expect(payload.steps[2]).toMatchObject({ parallelSeq: 1, branchIndex: 1 })
    expect(payload.steps[3]).toMatchObject({
      actionType: 'JOIN',
      targetType: 'NONE',
      parallelSeq: null,
      branchIndex: null
    })
    expect(payload.steps[4]).toMatchObject({ parallelSeq: null, branchIndex: null })
  })

  it('并行块缺少汇合步骤时阻止提交', async () => {
    wrapper = mountDialog(INVALID_PARALLEL_SCENE)
    await flushPromises()

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(hooks.updateScene).not.toHaveBeenCalled()
    expect(messageMock.warning.mock.calls[0][0]).toContain('缺少对应的并行汇合')
  })

  it('影子变更触发允许空标识符并提交变更侧', async () => {
    wrapper = mountDialog(SHADOW_SCENE)
    await flushPromises()

    expect(wrapper.text()).toContain('变更侧')
    expect(wrapper.find('input[placeholder="留空表示任意属性变更"]').exists()).toBe(true)

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    const payload = hooks.updateScene.mock.calls[0][1]
    expect(payload.triggerType).toBe('SHADOW')
    expect(payload.triggerShadowSide).toBe('ANY')
    expect(payload.triggerIdentifier).toBeNull()
    expect(payload.triggerOperator).toBeNull()
    expect(payload.timerCron).toBeNull()
  })
})