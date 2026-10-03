import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import api from '../axios'
import { propertyHistoryApi } from '../propertyHistory'

/** 构造一个 axios 成功响应体并交给自定义 adapter 返回。 */
function okResponse(data, status = 200) {
  return { data, status, statusText: 'OK', headers: {}, config: {} }
}

describe('api/propertyHistory#query', () => {
  let adapter
  let originalAdapter

  beforeEach(() => {
    // axios 请求拦截器会读取 auth store，测试环境需先激活一个 Pinia 实例。
    setActivePinia(createPinia())
    localStorage.clear()
    originalAdapter = api.defaults.adapter
    adapter = vi
      .fn()
      .mockResolvedValue(okResponse({ code: 200, message: 'ok', data: [] }))
    api.defaults.adapter = (config) => adapter(config)
  })

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    vi.clearAllMocks()
  })

  it('请求 /properties/history 并原样透传查询参数', async () => {
    const params = {
      deviceIds: '1,2',
      identifiers: 'temperature,humidity',
      startTime: '2026-10-02 00:00:00',
      endTime: '2026-10-03 00:00:00',
      bucket: '5m'
    }

    await propertyHistoryApi.query(params)

    expect(adapter).toHaveBeenCalledTimes(1)
    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    // 自定义 adapter 拿到的是未拼接的配置：baseURL 与 url 分开传递
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/properties/history')
    expect(config.params).toEqual(params)
  })

  it('未传参数时不注入伪 params', async () => {
    await propertyHistoryApi.query()

    const config = adapter.mock.calls[0][0]
    expect(config.url).toBe('/properties/history')
    expect(config.params).toBeUndefined()
  })

  it('返回完整 Result 包装体，交由调用方 unwrap', async () => {
    const payload = {
      code: 200,
      message: 'ok',
      data: [{ deviceId: 1, identifier: 'temperature', numeric: true, points: [] }]
    }
    adapter.mockResolvedValue(okResponse(payload))

    const result = await propertyHistoryApi.query({ deviceIds: '1', identifiers: 'temperature' })

    expect(result).toEqual(payload)
    expect(result.data).toHaveLength(1)
    expect(result.data[0].identifier).toBe('temperature')
  })
})
