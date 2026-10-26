import { describe, expect, it, vi, beforeEach } from 'vitest'

const get = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args)
  }
}))

const { exportApi } = await import('../export')

beforeEach(() => get.mockReset())

const DOWNLOAD = { responseType: 'blob', returnFullResponse: true }

describe('exportApi', () => {
  it('设备日志导出路径与筛选参数', () => {
    exportApi.deviceLogs('dev-1', { types: 'MESSAGE', keyword: 'x' })

    expect(get).toHaveBeenCalledWith('/devices/dev-1/logs/export', {
      ...DOWNLOAD,
      params: { types: 'MESSAGE', keyword: 'x' }
    })
  })

  it('属性趋势、命令记录与告警导出路径', () => {
    exportApi.propertyHistory('dev-1', { identifiers: 'temperature', bucket: '5m' })
    exportApi.commands('dev-1', { page: 1 })
    exportApi.alerts({ severity: 'CRITICAL' })

    expect(get).toHaveBeenNthCalledWith(1, '/devices/dev-1/properties/history/export', {
      ...DOWNLOAD,
      params: { identifiers: 'temperature', bucket: '5m' }
    })
    expect(get).toHaveBeenNthCalledWith(2, '/devices/dev-1/commands/export', {
      ...DOWNLOAD,
      params: { page: 1 }
    })
    expect(get).toHaveBeenNthCalledWith(3, '/alerts/export', {
      ...DOWNLOAD,
      params: { severity: 'CRITICAL' }
    })
  })

  it('设备台账与审计日志导出路径（T-53）', () => {
    exportApi.devices({ status: 'ONLINE', limit: 10000 })
    exportApi.auditLogs({ action: 'DELETE', limit: 10000 })

    expect(get).toHaveBeenNthCalledWith(1, '/devices/export', {
      ...DOWNLOAD,
      params: { status: 'ONLINE', limit: 10000 }
    })
    expect(get).toHaveBeenNthCalledWith(2, '/audit-logs/export', {
      ...DOWNLOAD,
      params: { action: 'DELETE', limit: 10000 }
    })
  })
})
