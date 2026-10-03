import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

/** 共享替身：vi.mock 会被提升到文件顶部，用 vi.hoisted 保证工厂执行时替身已就绪。 */
const mocks = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirmMock: vi.fn(() => Promise.resolve()),
  routerPush: vi.fn(),
  getList: vi.fn()
}))

/**
 * BoardDetail.vue 以裸 `ElMessage` / `ElMessageBox` 调用命令式 API，
 * 由 unplugin-auto-import 按 ElementPlusOnDemand 解析成细粒度路径，
 * 因此这里对具体模块路径打桩（与 DeviceLog.spec.js 一致）。
 */
vi.mock('element-plus/es/components/message/index.mjs', () => ({
  ElMessage: mocks.messageMock
}))
vi.mock('element-plus/es/components/message-box/index.mjs', () => ({
  ElMessageBox: { confirm: mocks.confirmMock }
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '7' } }),
  useRouter: () => ({ push: mocks.routerPush })
}))

/** 页面用真实 useQuery 拉设备列表以映射图例名称，这里只替换接口替身。 */
vi.mock('@/api/device', () => ({
  deviceApi: { getList: mocks.getList }
}))

/** 详情与整份保存由组合式函数承担，替换为可控替身。 */
const hooks = vi.hoisted(() => ({
  detail: null,
  loading: null,
  loadError: null,
  refetch: null,
  saving: null,
  save: null
}))

vi.mock('@/composables/useDashboards', async () => {
  const { ref: rf, computed } = await import('vue')
  hooks.detail = rf(null)
  hooks.loading = rf(false)
  hooks.loadError = rf(false)
  hooks.refetch = vi.fn()
  hooks.saving = rf(false)
  hooks.save = vi.fn(() => Promise.resolve({ id: 7 }))
  return {
    useDashboardDetail: () => ({
      detail: hooks.detail,
      config: computed(() => {
        const panels = hooks.detail.value?.config?.panels
        return { panels: Array.isArray(panels) ? panels : [] }
      }),
      loading: hooks.loading,
      loadError: hooks.loadError,
      refetch: hooks.refetch,
      saving: hooks.saving,
      save: hooks.save
    })
  }
})

import BoardDetail from '../BoardDetail.vue'

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'link', 'size', 'disabled', 'loading', 'plain'],
  emits: ['click'],
  template:
    '<button type="button" class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
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

/** BoardPanel 会真实发起属性历史查询并触达 ECharts，因此整块打桩。 */
const BoardPanelStub = {
  name: 'BoardPanel',
  props: ['panel', 'deviceNames'],
  template: '<div class="stub-board-panel"><span class="stub-board-panel__title">{{ panel && panel.title }}</span></div>'
}

const BoardPanelEditorStub = {
  name: 'BoardPanelEditor',
  props: ['visible', 'panel', 'deviceOptions', 'deviceLoading'],
  emits: ['update:visible', 'submit'],
  template:
    '<div class="stub-editor" :data-visible="String(visible)" :data-device-count="deviceOptions.length">' +
    '<button type="button" class="stub-editor__submit" ' +
    '@click="$emit(\'submit\', { title: \'新增面板\', deviceIds: [1], identifier: \'temperature\' })">submit</button>' +
    '</div>'
}

const STUBS = {
  ElButton: ElButtonStub,
  EmptyState: EmptyStateStub,
  PageHeader: PageHeaderStub,
  SvgIcon: SvgIconStub,
  BoardPanel: BoardPanelStub,
  BoardPanelEditor: BoardPanelEditorStub
}

/** 推进异步：请求 → 渲染 → 微任务，覆盖 vue-query 的异步链路。 */
async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
}

/** 一条面板配置，字段与后端 DashboardConfig.Panel 对齐。 */
function panel(overrides = {}) {
  return {
    title: '面板A',
    deviceIds: [1],
    identifier: 'temperature',
    bucket: 'auto',
    rangeDays: 1,
    chartType: 'line',
    aggregation: 'avg',
    ...overrides
  }
}

function detailWith(panels = [panel()]) {
  return { id: 7, name: '机房监控', config: { panels } }
}

describe('views/workbench/board/BoardDetail', () => {
  let queryClient
  let wrapper

  function mountPage() {
    wrapper = mount(BoardDetail, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
        stubs: STUBS,
        directives: { loading: {} }
      }
    })
    return wrapper
  }

  function buttonByText(text) {
    return wrapper.findAll('button').find((button) => button.text().includes(text))
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    hooks.detail.value = detailWith()
    hooks.loading.value = false
    hooks.loadError.value = false
    hooks.saving.value = false
    hooks.refetch.mockReset()
    hooks.save.mockReset().mockResolvedValue({ id: 7 })
    mocks.getList.mockReset().mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { records: [{ id: 1, deviceName: '传感器A' }] }
    })
    mocks.routerPush.mockReset()
    mocks.confirmMock.mockReset().mockResolvedValue(undefined)
    mocks.messageMock.success.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('挂载后展示看板名称与面板数量', async () => {
    mountPage()
    await settle()

    expect(wrapper.find('.stub-page-header__title').text()).toBe('机房监控')
    expect(wrapper.text()).toContain('共 1 个面板')
    expect(wrapper.findAll('.panel-card')).toHaveLength(1)
  })

  it('拉取设备列表并把 deviceId 映射为图例名称', async () => {
    mountPage()
    await settle()

    expect(mocks.getList).toHaveBeenCalledWith({ page: 1, size: 200 })
    expect(wrapper.findComponent(BoardPanelStub).props('deviceNames')).toEqual({ 1: '传感器A' })
  })

  it('加载失败时展示错误空态并可重试', async () => {
    hooks.detail.value = null
    hooks.loadError.value = true
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty__desc').text()).toContain('看板加载失败，请稍后重试')

    await buttonByText('重试').trigger('click')

    expect(hooks.refetch).toHaveBeenCalledTimes(1)
  })

  it('无面板时展示空态', async () => {
    hooks.detail.value = detailWith([])
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty__desc').text()).toContain('该看板还没有面板')
    expect(wrapper.find('.board-detail__toolbar').exists()).toBe(false)
  })

  it('点击「编辑看板」进入编辑态并展示工具条', async () => {
    mountPage()
    await settle()

    await buttonByText('编辑看板').trigger('click')
    await nextTick()

    expect(wrapper.find('.board-detail__toolbar').exists()).toBe(true)
    expect(wrapper.find('.board-detail__hint').text()).toContain('保存')
    expect(wrapper.findAll('.panel-card__toolbar')).toHaveLength(1)
    expect(wrapper.find('.stub-editor').exists()).toBe(true)
  })

  it('编辑态新增面板后追加到草稿并保持编辑态', async () => {
    mountPage()
    await settle()

    await buttonByText('编辑看板').trigger('click')
    await nextTick()

    await buttonByText('添加面板').trigger('click')
    await nextTick()

    const editor = wrapper.findComponent(BoardPanelEditorStub)
    expect(editor.props('visible')).toBe(true)
    expect(editor.props('panel')).toBeNull()

    editor.vm.$emit('submit', { title: '新增面板', deviceIds: [1], identifier: 'temperature' })
    await nextTick()

    expect(wrapper.findAll('.panel-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('新增面板')
    expect(wrapper.find('.board-detail__toolbar').exists()).toBe(true)
  })

  it('编辑态编辑已有面板时替换对应草稿项', async () => {
    mountPage()
    await settle()

    await buttonByText('编辑看板').trigger('click')
    await nextTick()

    await buttonByText('编辑').trigger('click')
    await nextTick()

    const editor = wrapper.findComponent(BoardPanelEditorStub)
    expect(editor.props('visible')).toBe(true)
    expect(editor.props('panel')).toMatchObject({ title: '面板A' })

    editor.vm.$emit('submit', panel({ title: '改名后的面板' }))
    await nextTick()

    expect(wrapper.findAll('.panel-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('改名后的面板')
    expect(wrapper.text()).not.toContain('面板A')
  })

  it('编辑态移除面板需二次确认', async () => {
    mountPage()
    await settle()

    await buttonByText('编辑看板').trigger('click')
    await nextTick()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(mocks.confirmMock).toHaveBeenCalledTimes(1)
    expect(mocks.confirmMock.mock.calls[0][0]).toContain('面板A')
    expect(wrapper.findAll('.panel-card')).toHaveLength(0)
    expect(wrapper.find('.stub-empty__desc').text()).toContain('还没有面板，点击上方「添加面板」开始')
  })

  it('取消移除面板时保留草稿项', async () => {
    mocks.confirmMock.mockRejectedValueOnce(new Error('cancel'))
    mountPage()
    await settle()

    await buttonByText('编辑看板').trigger('click')
    await nextTick()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(wrapper.findAll('.panel-card')).toHaveLength(1)
  })

  it('保存看板整份提交草稿并退出编辑态', async () => {
    mountPage()
    await settle()

    await buttonByText('编辑看板').trigger('click')
    await nextTick()

    await buttonByText('保存').trigger('click')
    await settle()

    expect(hooks.save).toHaveBeenCalledTimes(1)
    const [name, config] = hooks.save.mock.calls[0]
    expect(name).toBe('机房监控')
    expect(config.panels).toHaveLength(1)
    expect(config.panels[0]).toMatchObject({ title: '面板A', identifier: 'temperature' })
    expect(mocks.messageMock.success).toHaveBeenCalledWith('看板已保存')
    expect(wrapper.find('.board-detail__toolbar').exists()).toBe(false)
  })

  it('保存失败（返回 null）时保持编辑态且不提示成功', async () => {
    hooks.save.mockResolvedValue(null)
    mountPage()
    await settle()

    await buttonByText('编辑看板').trigger('click')
    await nextTick()

    await buttonByText('保存').trigger('click')
    await settle()

    expect(wrapper.find('.board-detail__toolbar').exists()).toBe(true)
    expect(mocks.messageMock.success).not.toHaveBeenCalled()
  })

  it('点击返回跳转看板列表', async () => {
    mountPage()
    await settle()

    await buttonByText('返回').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/workbench/boards')
  })
})
