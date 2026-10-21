import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args),
    post: (...args) => post(...args)
  }
}))

const { debugApi } = await import('../debug')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

/**
 * 调试 API（T-37）：路径与参数没有编译期约束，只能靠用例守住。
 */
describe('debugApi', () => {
  it('上下文走设备调试子路径', () => {
    debugApi.getContext('sensor-001')
    expect(get).toHaveBeenCalledWith('/devices/sensor-001/debug/context')
  })

  it('模拟上报走 POST 且透传消息类型', () => {
    debugApi.simulate('sensor-001', { messageType: 'data', payload: { temperature: 26.5 } })
    expect(post).toHaveBeenCalledWith('/devices/sensor-001/debug/simulate', {
      messageType: 'data',
      payload: { temperature: 26.5 }
    })
  })

  it('代码生成以 query 透传 target 与 language', () => {
    debugApi.getCode('sensor-001', { target: 'host', language: 'python' })
    expect(get).toHaveBeenCalledWith('/devices/sensor-001/debug/code', {
      params: { target: 'host', language: 'python' }
    })
  })
})
