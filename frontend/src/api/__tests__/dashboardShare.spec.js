import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import api from '../axios'
import { dashboardShareApi } from '../dashboardShare'

function okResponse(data, status = 200) {
  return { data, status, statusText: 'OK', headers: {}, config: {} }
}

describe('api/dashboardShare', () => {
  let adapter
  let originalAdapter

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    originalAdapter = api.defaults.adapter
    adapter = vi.fn().mockResolvedValue(okResponse({ code: 200, message: 'ok', data: null }))
    api.defaults.adapter = (config) => adapter(config)
  })

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    vi.clearAllMocks()
  })

  it('list 请求 GET /dashboards/shares', async () => {
    await dashboardShareApi.list()

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.url).toBe('/dashboards/shares')
  })

  it('create 使用 POST /dashboards/shares', async () => {
    const payload = { name: '大屏', dashboardIds: [1, 2], intervalSeconds: 15, enabled: true }

    await dashboardShareApi.create(payload)

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('post')
    expect(request.url).toBe('/dashboards/shares')
    expect(JSON.parse(request.data)).toEqual(payload)
  })

  it('update 使用 PUT /dashboards/shares/{id}', async () => {
    const payload = { name: '大屏', dashboardIds: [1], intervalSeconds: 30, enabled: false }

    await dashboardShareApi.update(7, payload)

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('put')
    expect(request.url).toBe('/dashboards/shares/7')
    expect(JSON.parse(request.data)).toEqual(payload)
  })

  it('remove 使用 DELETE /dashboards/shares/{id}', async () => {
    await dashboardShareApi.remove(7)

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('delete')
    expect(request.url).toBe('/dashboards/shares/7')
  })

  it('rotate 使用 POST /dashboards/shares/{id}/rotate', async () => {
    await dashboardShareApi.rotate(7)

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('post')
    expect(request.url).toBe('/dashboards/shares/7/rotate')
  })

  it('publicView 请求公开接口且不携带查询参数', async () => {
    await dashboardShareApi.publicView('token-abc')

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('get')
    expect(request.url).toBe('/public/dashboard-shares/token-abc')
    expect(request.params).toBeUndefined()
  })
})
