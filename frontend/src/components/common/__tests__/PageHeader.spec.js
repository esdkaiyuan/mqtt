import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from '../PageHeader.vue'

describe('components/common/PageHeader', () => {
  it('渲染标题与描述', () => {
    const wrapper = mount(PageHeader, {
      props: { title: '设备管理', desc: '查看与管理已接入设备' }
    })
    expect(wrapper.find('.app-page-header__title').text()).toBe('设备管理')
    expect(wrapper.find('.app-page-header__desc').text()).toBe('查看与管理已接入设备')
  })

  it('无描述时不渲染描述节点', () => {
    const wrapper = mount(PageHeader, { props: { title: '概览' } })
    expect(wrapper.find('.app-page-header__desc').exists()).toBe(false)
  })

  it('title 插槽优先于 title 属性', () => {
    const wrapper = mount(PageHeader, {
      props: { title: '忽略' },
      slots: { title: '<span class="custom-title">自定义标题</span>' }
    })
    expect(wrapper.find('.custom-title').text()).toBe('自定义标题')
  })

  it('actions 插槽存在时才渲染动作区', () => {
    const withoutActions = mount(PageHeader, { props: { title: '概览' } })
    expect(withoutActions.find('.app-page-header__actions').exists()).toBe(false)

    const withActions = mount(PageHeader, {
      props: { title: '概览' },
      slots: { actions: '<button class="act">新建</button>' }
    })
    expect(withActions.find('.app-page-header__actions .act').exists()).toBe(true)
  })
})