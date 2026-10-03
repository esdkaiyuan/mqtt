import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import api from '../axios'
import { dashboardApi } from '../dashboard'

/** 构造一个 axios 成功响应体并交给自定义 adapter 返回。 */
function okResponse(data, status = 200) {
  return { data, status, statusText: 'OK', headers: {}, config: {} }
}

describe('api/dashboard', () => {
  let adapter
  let originalAdapter

  beforeEach(() => {
    // axios 请求拦截器会读取 auth store，测试环境需先激活一个 Pinia 实例。
    setActivePinia(createPinia())
    localStorage.clear()
    originalAdapter = api.defaults.adapter
    adapter = vi
      .fn()
      .mockResolvedValue(okResponse({ code: 200, message: 'ok', data: null }))
    api.defaults.adapter = (config) => adapter(config)
  })

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    vi.clearAllMocks()
  })

  it('list 请求 GET /dashboards，无查询参数', async () => {
    await dashboardApi.list()

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/dashboards')
    expect(config.params).toBeUndefined()
  })

  it('create 使用 POST /dashboards 并把 config 序列化为 JSON 字符串', async () => {
    const config = { panels: [{ title: '温度', deviceIds: [1], identifier: 'temperature' }] }

    await dashboardApi.create({ name: '车间看板', config })

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('post')
    expect(request.url).toBe('/dashboards')
    // 归一化后 config 为 JSON 字符串，外层再被 axios 序列化为请求体
    expect(JSON.parse(request.data)).toEqual({ name: '车间看板', config: JSON.stringify(config) })
  })

  it('create 传入 config 字符串时不再二次编码', async () => {
    const configStr = JSON.stringify({ panels: [] })

    await dashboardApi.create({ name: '空看板', config: configStr })

    const request = adapter.mock.calls[0][0]
    expect(JSON.parse(request.data)).toEqual({ name: '空看板', config: configStr })
  })

  it('create 未传 config 时回退为空面板配置', async () => {
    await dashboardApi.create({ name: '默认看板' })

    const request = adapter.mock.calls[0][0]
    expect(JSON.parse(request.data)).toEqual({
      name: '默认看板',
      config: JSON.stringify({ panels: [] })
    })
  })

  it('detail 请求 GET /dashboards/{id}', async () => {
    await dashboardApi.detail(9)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.url).toBe('/dashboards/9')
  })

  it('update 使用 PUT /dashboards/{id} 并整份覆盖 name 与 config', async () => {
    const config = { panels: [] }

    await dashboardApi.update(9, { name: '改名看板', config })

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('put')
    expect(request.url).toBe('/dashboards/9')
    expect(JSON.parse(request.data)).toEqual({ name: '改名看板', config: JSON.stringify(config) })
  })

  it('remove 使用 DELETE /dashboards/{id}', async () => {
    await dashboardApi.remove(9)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('delete')
    expect(config.url).toBe('/dashboards/9')
  })

  it('返回完整 Result 包装体，交由调用方 unwrap', async () => {
    const payload = { code: 200, message: 'ok', data: [{ id: 1, name: '看板', panelCount: 2 }] }
    adapter.mockResolvedValue(okResponse(payload))

    const result = await dashboardApi.list()

    expect(result).toEqual(payload)
    expect(result.data[0].panelCount).toBe(2)
  })
})
