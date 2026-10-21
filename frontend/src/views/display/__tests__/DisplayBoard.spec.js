import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

const mocks = vi.hoisted(() => ({
  publicView: vi.fn()
}))

vi.mock('@/api/dashboardShare', () => ({
  dashboardShareApi: { publicView: mocks.publicView }
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { token: 'token-abc' } })
}))

import DisplayBoard from '../DisplayBoard.vue'

const BoardPanelStub = {
  name: 'BoardPanel',
  props: {
    panel: { type: Object, required: true },
    deviceNames: { type: Object, default: () => ({}) },
    refreshInterval: { type: Number, default: 0 },
    dark: { type: Boolean, default: false }
  },
  template:
    '<div class="stub-panel" :data-dark="dark" :data-refresh="refreshInterval">{{ panel.title }}</div>'
}
const ElButtonStub = {
  name: 'ElButton',
  props: ['size'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  BoardPanel: BoardPanelStub,
  ElButton: ElButtonStub,
  SvgIcon: { name: 'SvgIcon', props: ['name', 'size'], template: '<span class="stub-icon" />' }
}

function sharePayload() {
  return {
    name: '产线大屏',
    intervalSeconds: 20,
    deviceNames: { 100: '设备A' },
    dashboards: [
      {
        id: 1,
        name: '一号看板',
        config: { panels: [{ title: '温度', deviceIds: [100], identifier: 'temperature' }] },
        updatedAt: '2026-10-21T10:00:00'
      },
      {
        id: 2,
        name: '二号看板',
        config: { panels: [{ title: '湿度', deviceIds: [100], identifier: 'humidity' }] },
        updatedAt: '2026-10-21T10:00:00'
      }
    ]
  }
}

async function mountDisplay() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
  })
  const wrapper = mount(DisplayBoard, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: STUBS
    }
  })
  await flushPromises()
  await nextTick()
  await flushPromises()
  return { wrapper, queryClient }
}

describe('views/display/DisplayBoard', () => {
  beforeEach(() => {
    mocks.publicView.mockReset()
    mocks.publicView.mockResolvedValue({ code: 200, message: 'ok', data: sharePayload() })
  })

  it('渲染分享标题、首个看板并深色自动刷新面板', async () => {
    const { wrapper } = await mountDisplay()

    expect(wrapper.find('.display-title').text()).toBe('产线大屏')
    expect(wrapper.find('.display-board-title').text()).toBe('一号看板')
    expect(wrapper.find('.display-clock').exists()).toBe(true)

    const panel = wrapper.find('.stub-panel')
    expect(panel.text()).toBe('温度')
    expect(panel.attributes('data-dark')).toBe('true')
    expect(panel.attributes('data-refresh')).toBe('60')
  })

  it('点击轮播圆点切换看板', async () => {
    const { wrapper } = await mountDisplay()

    await wrapper.findAll('.display-dot')[1].trigger('click')
    await nextTick()

    expect(wrapper.find('.display-board-title').text()).toBe('二号看板')
    expect(wrapper.find('.stub-panel').text()).toBe('湿度')
  })

  it('无效令牌展示失效提示', async () => {
    mocks.publicView.mockRejectedValue(new Error('404'))
    const { wrapper } = await mountDisplay()

    expect(wrapper.text()).toContain('链接无效或已失效')
  })

  it('点击全屏按钮调用浏览器全屏接口', async () => {
    const requestFullscreen = vi.fn().mockResolvedValue()
    document.documentElement.requestFullscreen = requestFullscreen
    const { wrapper } = await mountDisplay()

    const button = wrapper.find('button[title="全屏"]')
    await button.trigger('click')

    expect(requestFullscreen).toHaveBeenCalled()
  })
})
