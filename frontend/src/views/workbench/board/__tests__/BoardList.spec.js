import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 共享替身：vi.mock 会被提升到文件顶部，用 vi.hoisted 保证工厂执行时替身已就绪。
 */
const mocks = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirmMock: vi.fn(() => Promise.resolve()),
  routerPush: vi.fn(),
  detail: vi.fn()
}))

/**
 * BoardList.vue 以裸 `ElMessage` / `ElMessageBox` 调用命令式 API，
 * 由 unplugin-auto-import 按 ElementPlusOnDemand 解析成细粒度路径
 * （`element-plus/es/components/<dir>/index.mjs`），因此这里对具体模块路径打桩，
 * 而不是 `element-plus` 主包（见 vite.config.js 的 ElementPlusOnDemand）。
 */
vi.mock('element-plus/es/components/message/index.mjs', () => ({
  ElMessage: mocks.messageMock
}))
vi.mock('element-plus/es/components/message-box/index.mjs', () => ({
  ElMessageBox: { confirm: mocks.confirmMock }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush })
}))

/** 重命名时页面会按 id 拉一次详情取出原 config，这里用替身返回 Result 包装体。 */
vi.mock('@/api/dashboard', () => ({
  dashboardApi: { detail: mocks.detail }
}))

/** 列表与增删改由组合式函数承担，替换为可控替身，聚焦页面交互本身。 */
const hooks = vi.hoisted(() => ({
  dashboards: null,
  loading: null,
  loadError: null,
  refetch: null,
  saving: null,
  create: null,
  update: null,
  remove: null
}))

vi.mock('@/composables/useDashboards', async () => {
  const { ref: rf } = await import('vue')
  hooks.dashboards = rf([])
  hooks.loading = rf(false)
  hooks.loadError = rf(false)
  hooks.refetch = vi.fn()
  hooks.saving = rf(false)
  hooks.create = vi.fn(() => Promise.resolve({ id: 1 }))
  hooks.update = vi.fn(() => Promise.resolve({ id: 1 }))
  hooks.remove = vi.fn(() => Promise.resolve())
  return {
    useDashboards: () => ({
      dashboards: hooks.dashboards,
      loading: hooks.loading,
      loadError: hooks.loadError,
      refetch: hooks.refetch,
      saving: hooks.saving,
      create: hooks.create,
      update: hooks.update,
      remove: hooks.remove
    })
  }
})

import BoardList from '../BoardList.vue'

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'link', 'size', 'disabled', 'loading'],
  emits: ['click'],
  template:
    '<button type="button" class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
}

const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input class="stub-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}

const ElFormStub = { name: 'ElForm', template: '<form class="stub-form"><slot /></form>' }
const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  template: '<div class="stub-form-item"><slot /></div>'
}
const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  emits: ['update:modelValue'],
  template:
    '<div v-if="modelValue" class="stub-dialog">' +
    '<span class="stub-dialog__title">{{ title }}</span>' +
    '<slot /><slot name="footer" /></div>'
}

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description'],
  template: '<div class="stub-empty"><span class="stub-empty__desc">{{ description }}</span><slot /></div>'
}

const PageHeaderStub = {
  name: 'PageHeader',
  props: ['title', 'desc'],
  template:
    '<div class="stub-page-header">' +
    '<span class="stub-page-header__title">{{ title }}</span>' +
    '<span class="stub-page-header__desc">{{ desc }}</span>' +
    '<slot name="title" /><slot name="actions" /></div>'
}

const SvgIconStub = { name: 'SvgIcon', props: ['name', 'size'], template: '<span class="stub-icon" />' }

const STUBS = {
  ElButton: ElButtonStub,
  ElInput: ElInputStub,
  ElForm: ElFormStub,
  ElFormItem: ElFormItemStub,
  ElDialog: ElDialogStub,
  EmptyState: EmptyStateStub,
  PageHeader: PageHeaderStub,
  SvgIcon: SvgIconStub
}

/** 推进异步：请求 → 渲染 → 微任务。 */
async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('views/workbench/board/BoardList', () => {
  let wrapper

  function mountPage() {
    wrapper = mount(BoardList, { global: { stubs: STUBS } })
    return wrapper
  }

  function buttonByText(text) {
    return wrapper.findAll('button').find((button) => button.text().includes(text))
  }

  function board(overrides = {}) {
    return { id: 3, name: '机房监控', panelCount: 2, updatedAt: '2026-10-01T08:00:00', ...overrides }
  }

  beforeEach(() => {
    hooks.dashboards.value = []
    hooks.loading.value = false
    hooks.loadError.value = false
    hooks.saving.value = false
    hooks.refetch.mockReset()
    hooks.create.mockReset().mockResolvedValue({ id: 1 })
    hooks.update.mockReset().mockResolvedValue({ id: 1 })
    hooks.remove.mockReset().mockResolvedValue(undefined)
    mocks.routerPush.mockReset()
    mocks.detail.mockReset()
    mocks.confirmMock.mockReset().mockResolvedValue(undefined)
    mocks.messageMock.success.mockClear()
    mocks.messageMock.warning.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('无看板时渲染空态提示', async () => {
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty__desc').text()).toContain('暂无看板，点击「新建看板」开始')
    expect(wrapper.find('.board-card').exists()).toBe(false)
  })

  it('加载失败且无数据时渲染错误空态并可重试', async () => {
    hooks.loadError.value = true
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty__desc').text()).toContain('看板列表加载失败，请稍后重试')

    await buttonByText('重试').trigger('click')

    expect(hooks.refetch).toHaveBeenCalledTimes(1)
  })

  it('有看板时渲染卡片并展示面板数与更新时间', async () => {
    hooks.dashboards.value = [board()]
    mountPage()
    await settle()

    expect(wrapper.findAll('.board-card')).toHaveLength(1)
    expect(wrapper.find('.board-card__title').text()).toBe('机房监控')
    expect(wrapper.find('.board-card__count').text()).toBe('2 个面板')
    expect(wrapper.find('.board-card__time').text()).toContain('更新于')
  })

  it('点击卡片进入看板详情', async () => {
    hooks.dashboards.value = [board()]
    mountPage()
    await settle()

    await wrapper.find('.board-card').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/workbench/boards/3')
  })

  it('新建看板：填写名称后提交调用 create 并关闭弹窗', async () => {
    mountPage()
    await settle()

    await buttonByText('新建看板').trigger('click')
    await nextTick()

    expect(wrapper.find('.stub-dialog').exists()).toBe(true)
    expect(wrapper.find('.stub-dialog__title').text()).toBe('新建看板')

    await wrapper.find('.stub-input').setValue('一号机房')
    await buttonByText('创建').trigger('click')
    await settle()

    expect(hooks.create).toHaveBeenCalledWith({ name: '一号机房', config: { panels: [] } })
    expect(mocks.messageMock.success).toHaveBeenCalledWith('看板已创建')
    expect(wrapper.find('.stub-dialog').exists()).toBe(false)
  })

  it('看板名称为空时提示且不提交', async () => {
    mountPage()
    await settle()

    await buttonByText('新建看板').trigger('click')
    await nextTick()

    await buttonByText('创建').trigger('click')
    await settle()

    expect(mocks.messageMock.warning).toHaveBeenCalledWith('请填写看板名称')
    expect(hooks.create).not.toHaveBeenCalled()
    expect(wrapper.find('.stub-dialog').exists()).toBe(true)
  })

  it('重命名看板时沿用服务端原配置整份提交', async () => {
    hooks.dashboards.value = [board({ id: 5, name: '旧名字', panelCount: 1 })]
    mocks.detail.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { id: 5, name: '旧名字', config: { panels: [{ title: '面板A' }] } }
    })
    mountPage()
    await settle()

    await buttonByText('重命名').trigger('click')
    await nextTick()

    expect(wrapper.find('.stub-dialog__title').text()).toBe('重命名看板')

    await wrapper.find('.stub-input').setValue('新名字')
    await buttonByText('保存').trigger('click')
    await settle()

    expect(mocks.detail).toHaveBeenCalledWith(5)
    expect(hooks.update).toHaveBeenCalledWith(5, {
      name: '新名字',
      config: { panels: [{ title: '面板A' }] }
    })
    expect(mocks.messageMock.success).toHaveBeenCalledWith('看板已重命名')
  })

  it('删除看板需二次确认后调用 remove', async () => {
    hooks.dashboards.value = [board({ id: 9, name: '待删看板', panelCount: 0, updatedAt: null })]
    mountPage()
    await settle()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(mocks.confirmMock).toHaveBeenCalledTimes(1)
    expect(mocks.confirmMock.mock.calls[0][0]).toContain('待删看板')
    expect(hooks.remove).toHaveBeenCalledWith(9)
    expect(mocks.messageMock.success).toHaveBeenCalledWith('看板已删除')
  })

  it('取消删除时不调用 remove', async () => {
    hooks.dashboards.value = [board({ id: 9, name: '待删看板', panelCount: 0, updatedAt: null })]
    mocks.confirmMock.mockRejectedValueOnce(new Error('cancel'))
    mountPage()
    await settle()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(hooks.remove).not.toHaveBeenCalled()
    expect(mocks.messageMock.success).not.toHaveBeenCalled()
  })

  it('重命名时详情拉取失败则保持弹窗不关闭', async () => {
    hooks.dashboards.value = [board({ id: 5, name: '旧名字', panelCount: 1 })]
    mocks.detail.mockRejectedValue(new Error('boom'))
    mountPage()
    await settle()

    await buttonByText('重命名').trigger('click')
    await nextTick()
    await buttonByText('保存').trigger('click')
    await settle()

    expect(hooks.update).not.toHaveBeenCalled()
    expect(wrapper.find('.stub-dialog').exists()).toBe(true)
  })
})
