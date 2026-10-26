import { describe, expect, it, vi, beforeEach } from 'vitest'

const get = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args)
  }
}))

const { auditLogApi } = await import('../auditLog')

beforeEach(() => get.mockReset())

describe('auditLogApi', () => {
  it('查询审计日志携带筛选与分页参数', () => {
    const params = { action: 'DELETE', resourceType: 'DEVICE', pageNum: 1, pageSize: 20 }
    auditLogApi.list(params)

    expect(get).toHaveBeenCalledWith('/audit-logs', { params })
  })
})