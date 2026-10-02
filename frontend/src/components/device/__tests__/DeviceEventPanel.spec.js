import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DeviceEventPanel from '../DeviceEventPanel.vue'

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const ElTagStub = {
  name: 'ElTag',
  props: ['type'],
  template: '<span class="stub-tag" :data-type="type"><slot /></span>'
}

const ElPaginationStub = {
  name: 'ElPagination',
  props: ['currentPage', 'pageSize', 'total'],
  emits: ['current-change'],
  template:
    '<div class="stub-pagination"><button class="stub-pagination__next" @click="$emit(\'current-change\', currentPage + 1)">next</button></div>'
}

const STUBS = {
  EmptyState: EmptyStateStub,
  ElTag: ElTagStub,
  ElPagination: ElPaginationStub
}

function mountPanel(props) {
  return mount(DeviceEventPanel, {
    props,
    global: { stubs: STUBS, directives: { loading: {} } }
  })
}

describe('components/device/DeviceEventPanel', () => {
  it('空态渲染占位提示且不渲染分页', () => {
    const wrapper = mountPanel({ events: [], total: 0 })

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无事件记录')
    expect(wrapper.find('.stub-pagination').exists()).toBe(false)
  })

  it('有数据时按时间线渲染标识符、事件类型与美化输出', () => {
    const wrapper = mountPanel({
      events: [
        {
          id: 1,
          identifier: 'highTemp',
          eventType: 'alert',
          outputData: '{"temperature":80}',
          reportedAt: '2026-10-02T10:00:00'
        }
      ],
      total: 1
    })

    expect(wrapper.find('.stub-empty').exists()).toBe(false)
    expect(wrapper.text()).toContain('highTemp')
    expect(wrapper.find('.stub-tag').attributes('data-type')).toBe('warning')
    // JSON 输出被美化（含换行缩进）
    expect(wrapper.find('.event-timeline__data').text()).toContain('\n')
  })

  it('输出参数非 JSON 时原样透出，不丢失信息', () => {
    const wrapper = mountPanel({
      events: [{ id: 2, identifier: 'rawEvent', eventType: 'info', outputData: 'plain-text', reportedAt: null }],
      total: 1
    })

    expect(wrapper.find('.event-timeline__data').text()).toBe('plain-text')
    expect(wrapper.find('.stub-tag').attributes('data-type')).toBe('info')
  })

  it('total 大于 0 时渲染分页并转发 current-change', async () => {
    const wrapper = mountPanel({
      events: [{ id: 3, identifier: 'e', eventType: 'fault', outputData: '', reportedAt: null }],
      total: 25,
      page: 1,
      size: 10
    })

    const pagination = wrapper.findComponent(ElPaginationStub)
    expect(pagination.exists()).toBe(true)

    await wrapper.find('.stub-pagination__next').trigger('click')

    expect(wrapper.emitted('page-change')).toBeTruthy()
    expect(wrapper.emitted('page-change')[0]).toEqual([2])
  })
})