import { describe, expect, it, vi, beforeEach } from 'vitest'

const get = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args)
  }
}))

const { deviceApi } = await import('../device')

beforeEach(() => get.mockReset())

describe('deviceApi shadow summary (T-42)', () => {
  it('聚合端点不带参数时返回本人全部设备', () => {
    deviceApi.getShadowSummary()

    expect(get).toHaveBeenCalledWith('/devices/shadow-summary', { params: undefined })
  })

  it('显式设备集合按原样透传，由后端做归属与数量校验', () => {
    deviceApi.getShadowSummary({ deviceIds: '1,2' })

    expect(get).toHaveBeenCalledWith('/devices/shadow-summary', { params: { deviceIds: '1,2' } })
  })
})
