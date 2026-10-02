import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'
import DocsLayout from '../DocsLayout.vue'

/**
 * DocsLayout 的窄屏抽屉由 JS matchMedia 驱动，而 jsdom 不实现真实的媒体查询，
 * 这里用替身控制断点命中结果，并保留 change 监听以便手动触发档位切换。
 */
const media = { matches: false, changeHandlers: [] }

function installMatchMedia(matches) {
  media.matches = matches
  media.changeHandlers = []
  window.matchMedia = vi.fn((query) => ({
    // 用 getter 读取，组件持有的 MediaQueryList 才能反映后续档位变化
    get matches() {
      return media.matches
    },
    media: query,
    addEventListener: (_type, handler) => media.changeHandlers.push(handler),
    removeEventListener: () => {},
    addListener() {},
    removeListener() {},
    dispatchEvent: () => false
  }))
}

// 站点头替身：忠实反映契约（tocToggle 入参 / toggleToc 事件），其显隐本身是纯 CSS 行为
const SiteNavStub = {
  name: 'SiteNav',
  props: { tocToggle: { type: Boolean, default: false } },
  emits: ['toggleToc'],
  template: `
    <nav class="site-nav-stub">
      <button v-if="tocToggle" class="toc-entry" type="button" @click="$emit('toggleToc')">目录</button>
    </nav>
  `
}

const DocsNavStub = { name: 'DocsNav', template: '<nav class="docs-nav-stub" />' }
const DocsTocStub = { name: 'DocsToc', template: '<nav class="docs-toc-stub" />' }
const SiteFooterStub = { name: 'SiteFooter', template: '<footer class="site-footer-stub" />' }
const ElDrawerStub = {
  name: 'ElDrawer',
  props: { modelValue: { type: Boolean, default: false } },
  emits: ['update:modelValue'],
  template: '<aside v-if="modelValue" class="el-drawer-stub"><slot /></aside>'
}

const STUBS = {
  SiteNav: SiteNavStub,
  DocsNav: DocsNavStub,
  DocsToc: DocsTocStub,
  SiteFooter: SiteFooterStub,
  ElDrawer: ElDrawerStub,
  ElIcon: { name: 'ElIcon', template: '<i><slot /></i>' }
}

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/docs', redirect: '/docs/a' },
      { path: '/docs/a', component: { template: '<div />' } },
      { path: '/docs/b', component: { template: '<div />' } }
    ]
  })
}

async function mountLayout() {
  const router = createTestRouter()
  await router.push('/docs/a')
  await router.isReady()
  const wrapper = mount(DocsLayout, {
    global: { plugins: [router], stubs: STUBS }
  })
  return { wrapper, router }
}

describe('components/layout/DocsLayout 窄屏左导航抽屉', () => {
  beforeEach(() => {
    installMatchMedia(false)
  })

  it('窄屏（<1024px）提供目录入口，点击后展开抽屉并渲染文档目录', async () => {
    installMatchMedia(true)
    const { wrapper } = await mountLayout()

    expect(wrapper.find('.toc-entry').exists()).toBe(true)
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(false)

    await wrapper.find('.toc-entry').trigger('click')

    const drawer = wrapper.find('.el-drawer-stub')
    expect(drawer.exists()).toBe(true)
    expect(drawer.text()).toContain('文档目录')
    expect(drawer.find('.docs-nav-stub').exists()).toBe(true)
  })

  it('窄屏下抽屉内跳转路由后自动收起', async () => {
    installMatchMedia(true)
    const { wrapper, router } = await mountLayout()

    await wrapper.find('.toc-entry').trigger('click')
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(true)

    await router.push('/docs/b')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.el-drawer-stub').exists()).toBe(false)
  })

  it('宽屏时不提供目录入口，也不渲染抽屉', async () => {
    const { wrapper } = await mountLayout()

    expect(wrapper.find('.toc-entry').exists()).toBe(false)
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(false)
    // 宽屏下左导航仍由常驻栏承载
    expect(wrapper.find('.docs-layout__side .docs-nav-stub').exists()).toBe(true)
  })

  it('断点变化时同步档位：宽屏切窄屏出现入口，切回宽屏收起抽屉', async () => {
    const { wrapper } = await mountLayout()
    expect(wrapper.find('.toc-entry').exists()).toBe(false)

    media.matches = true
    media.changeHandlers.forEach((handler) => handler({ matches: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.toc-entry').exists()).toBe(true)

    await wrapper.find('.toc-entry').trigger('click')
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(true)

    media.matches = false
    media.changeHandlers.forEach((handler) => handler({ matches: false }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.toc-entry').exists()).toBe(false)
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(false)
  })
})