import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

const { productApi } = await import('../product')

beforeEach(() => get.mockReset())

describe('productApi driver config (T-31 P4)', () => {
  it('驱动配置走产品子路径，避免列表暴露内网地址', () => {
    productApi.getDriverConfig(7)

    expect(get).toHaveBeenCalledWith('/products/7/driver-config')
  })
})
