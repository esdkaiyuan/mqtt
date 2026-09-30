import { afterEach, vi } from 'vitest'

/**
 * jsdom 未实现的浏览器 API 兜底。
 * 组件/composable 在挂载路径上可能间接调用到它们。
 */
if (!window.matchMedia) {
  window.matchMedia = () => ({
    matches: false,
    media: '',
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent: () => false
  })
}

if (!window.HTMLElement.prototype.scrollIntoView) {
  window.HTMLElement.prototype.scrollIntoView = () => {}
}

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

afterEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})
