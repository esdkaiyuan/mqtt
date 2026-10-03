import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DeviceLogTimeline from '../DeviceLogTimeline.vue'

const ElTimelineStub = {
  name: 'ElTimeline',
  template: '<div class="stub-timeline"><slot /></div>'
}

const ElTimelineItemStub = {
  name: 'ElTimelineItem',
  props: ['type', 'hollow', 'timestamp', 'placement'],
  template:
    '<div class="stub-timeline-item" :data-type="type" :data-hollow="String(hollow)" :data-time="timestamp"><slot /></div>'
}

const ElTagStub = {
  name: 'ElTag',
  props: ['type', 'size', 'effect'],
  template: '<span class="stub-tag" :data-type="type"><slot /></span>'
}

const ElPaginationStub = {
  name: 'ElPagination',
  props: ['currentPage', 'pageSize', 'total', 'layout', 'background'],
  emits: ['current-change'],
  template:
    '<div class="stub-pagination"><button class="stub-pagination__next" @click="$emit(\'current-change\', currentPage + 1)">next</button></div>'
}

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const STUBS = {
  ElTimeline: ElTimelineStub,
  ElTimelineItem: ElTimelineItemStub,
  ElTag: ElTagStub,
  ElPagination: ElPaginationStub,
  EmptyState: EmptyStateStub
}

function mountTimeline(props) {
  return mount(DeviceLogTimeline, {
    props,
    global: { stubs: STUBS, directives: { loading: {} } }
  })
}

const COMMAND_ITEM = {
  logId: 'c-1',
  logType: 'COMMAND',
  title: '命令',
  direction: 'PUBLISH',
  occurredAt: '2026-10-02T10:01:00',
  identifier: 'reboot',
  status: 'ACKED',
  callType: 'sync',
  qos: 1,
  commandId: 'cmd-1',
  attemptCount: 2,
  payload: '{"delay":3}',
  errorMessage: null,
  correlationRole: 'REQUEST'
}

describe('components/device/DeviceLogTimeline', () => {
  it('空态渲染占位提示且不渲染分页', () => {
    const wrapper = mountTimeline({ items: [], total: 0 })

    expect(wrapper.find('.stub-timeline').exists()).toBe(false)
    expect(wrapper.find('.stub-empty').text()).toContain('当前筛选范围内没有日志')
    expect(wrapper.find('.stub-pagination').exists()).toBe(false)
  })

  it('按类型渲染节点着色与中文标签', () => {
    const wrapper = mountTimeline({
      items: [
        { logId: 'm-1', logType: 'MESSAGE', title: '上行报文', topic: 'dev/1/up' },
        { logId: 'e-1', logType: 'EVENT', title: '事件' },
        { logId: 's-1', logType: 'STATUS', title: '设备上线' }
      ],
      total: 3
    })

    const nodes = wrapper.findAll('.stub-timeline-item')
    expect(nodes).toHaveLength(3)
    expect(nodes[0].attributes('data-type')).toBe('info')
    expect(nodes[1].attributes('data-type')).toBe('warning')
    expect(nodes[2].attributes('data-type')).toBe('success')

    const labels = wrapper.findAll('.stub-tag').map((tag) => tag.text())
    expect(labels).toEqual(['报文', '事件', '状态'])
  })

  it('罗列来源专属字段并翻译调用方式', () => {
    const wrapper = mountTimeline({ items: [COMMAND_ITEM], total: 1 })

    const facts = wrapper.findAll('.log-item__fact').map((fact) => fact.text())
    expect(facts).toContain('标识符reboot')
    expect(facts).toContain('状态ACKED')
    expect(facts).toContain('调用同步')
    expect(facts).toContain('QoS1')
    expect(facts).toContain('命令 IDcmd-1')
    expect(facts).toContain('重试2')
    expect(facts).toHaveLength(6)
  })

  it('回执条目标注「回执」并渲染空心节点', () => {
    const wrapper = mountTimeline({
      items: [{ ...COMMAND_ITEM, logId: 'r-1', correlationRole: 'REPLY' }],
      total: 1
    })

    const node = wrapper.find('.stub-timeline-item')
    expect(node.attributes('data-hollow')).toBe('true')

    const tags = wrapper.findAll('.stub-tag')
    expect(tags).toHaveLength(2)
    expect(tags[1].text()).toBe('回执')
    expect(tags[1].attributes('data-type')).toBe('success')
  })

  it('payload 为 JSON 时美化输出，非 JSON 时原样透出', () => {
    const json = mountTimeline({ items: [{ ...COMMAND_ITEM, logId: 'j-1' }], total: 1 })
    expect(json.find('.log-item__payload').text()).toContain('\n')
    expect(json.find('.log-item__payload').text()).toContain('"delay": 3')

    const raw = mountTimeline({
      items: [{ ...COMMAND_ITEM, logId: 'j-2', payload: 'plain-text' }],
      total: 1
    })
    expect(raw.find('.log-item__payload').text()).toBe('plain-text')
  })

  it('无 payload 的条目不渲染报文块', () => {
    const wrapper = mountTimeline({
      items: [{ ...COMMAND_ITEM, logId: 'n-1', payload: null }],
      total: 1
    })

    expect(wrapper.find('.log-item__payload').exists()).toBe(false)
  })

  it('失败条目显示失败原因', () => {
    const wrapper = mountTimeline({
      items: [{ ...COMMAND_ITEM, logId: 'f-1', errorMessage: '等待回执超时' }],
      total: 1
    })

    expect(wrapper.find('.log-item__error').text()).toContain('等待回执超时')
  })

  it('标题缺失时回退展示 logId', () => {
    const wrapper = mountTimeline({
      items: [{ logId: 'x-9', logType: 'MESSAGE', title: '', direction: 'PUBLISH' }],
      total: 1
    })

    expect(wrapper.find('.log-item__title').text()).toBe('x-9')
  })

  it('total 大于 0 时渲染分页并转发 current-change', async () => {
    const wrapper = mountTimeline({ items: [COMMAND_ITEM], total: 42, page: 1, size: 20 })

    const pagination = wrapper.findComponent(ElPaginationStub)
    expect(pagination.exists()).toBe(true)
    expect(pagination.props('total')).toBe(42)

    await wrapper.find('.stub-pagination__next').trigger('click')

    expect(wrapper.emitted('page-change')).toBeTruthy()
    expect(wrapper.emitted('page-change')[0]).toEqual([2])
  })
})
