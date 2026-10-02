import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }
}))

import { ElMessage } from 'element-plus'
import CodeBlock from '../CodeBlock.vue'

function mockClipboard(writeText) {
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText },
    configurable: true
  })
}

describe('components/docs/CodeBlock', () => {
  beforeEach(() => {
    mockClipboard(vi.fn().mockResolvedValue())
  })

  it('渲染语言标签', () => {
    const wrapper = mount(CodeBlock, { props: { code: '{}', language: 'json' } })
    expect(wrapper.find('.code-block__lang').text()).toBe('json')
  })

  it('对已注册语言做语法着色', () => {
    const wrapper = mount(CodeBlock, {
      props: { code: '{"name":"mqtt"}', language: 'json' }
    })
    expect(wrapper.find('.code-block__pre .hljs-attr').exists()).toBe(true)
    expect(wrapper.find('.code-block__pre .hljs-string').exists()).toBe(true)
  })

  it('未注册语言回退为转义纯文本，不注入标签', () => {
    const wrapper = mount(CodeBlock, {
      props: { code: '<script>alert(1)</script>', language: 'unknown' }
    })
    const code = wrapper.find('.code-block__pre code')
    expect(code.text()).toBe('<script>alert(1)</script>')
    expect(code.find('script').exists()).toBe(false)
    expect(code.html()).not.toContain('<script>')
  })

  it('复制成功写入剪贴板并切换按钮文案', async () => {
    const writeText = vi.fn().mockResolvedValue()
    mockClipboard(writeText)
    const wrapper = mount(CodeBlock, {
      props: { code: 'mosquitto_pub -t demo', language: 'bash' }
    })

    await wrapper.find('.code-block__copy').trigger('click')

    expect(writeText).toHaveBeenCalledWith('mosquitto_pub -t demo')
    expect(wrapper.find('.code-block__copy').text()).toBe('已复制')
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('复制失败时提示错误且不切换文案', async () => {
    mockClipboard(vi.fn().mockRejectedValue(new Error('denied')))
    const wrapper = mount(CodeBlock, { props: { code: 'echo hi', language: 'bash' } })

    await wrapper.find('.code-block__copy').trigger('click')

    expect(ElMessage.error).toHaveBeenCalled()
    expect(wrapper.find('.code-block__copy').text()).toBe('复制')
  })
})