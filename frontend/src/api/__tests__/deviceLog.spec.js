import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import api from '../axios'
import { deviceApi } from '../device'

/** 构造一个 axios 成功响应体并交给自定义 adapter 返回。 */
function okResponse(data, status = 200) {
  return { data, status, statusText: 'OK', headers: {}, config: {} }
}

describe('api/device#getDeviceLogs', () => {
  let adapter
  let originalAdapter

  beforeEach(() => {
    // axios 请求拦截器会读取 auth store，测试环境需先激活一个 Pinia 实例。
    setActivePinia(createPinia())
    localStorage.clear()
    originalAdapter = api.defaults.adapter
    adapter = vi
      .fn()
      .mockResolvedValue(okResponse({ code: 200, message: 'ok', data: { records: [], total: 0 } }))
    api.defaults.adapter = (config) => adapter(config)
  })

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    vi.clearAllMocks()
  })

  it('按 deviceId 拼接日志路径并原样透传分页与筛选参数', async () => {
    const params = {
      pageNum: 2,
      pageSize: 20,
      types: 'MESSAGE,COMMAND',
      startTime: '2026-10-02 00:00:00',
      endTime: '2026-10-03 00:00:00',
      keyword: 'reboot'
    }

    await deviceApi.getDeviceLogs(7, params)

    expect(adapter).toHaveBeenCalledTimes(1)
    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    // 自定义 adapter 拿到的是未拼接的配置：baseURL 与 url 分开传递
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/devices/7/logs')
    expect(config.params).toEqual(params)
  })

  it('deviceId 为字符串时同样正确拼接路径', async () => {
    await deviceApi.getDeviceLogs('sensor-1', { pageNum: 1, pageSize: 20 })

    expect(adapter.mock.calls[0][0].url).toBe('/devices/sensor-1/logs')
  })

  it('未传参数时不注入伪 params', async () => {
    await deviceApi.getDeviceLogs(7)

    const config = adapter.mock.calls[0][0]
    expect(config.url).toBe('/devices/7/logs')
    expect(config.params).toBeUndefined()
  })

  it('返回完整 Result 包装体，交由调用方 unwrap', async () => {
    const payload = { code: 200, message: 'ok', data: { records: [{ logId: 'l-1' }], total: 1 } }
    adapter.mockResolvedValue(okResponse(payload))

    const result = await deviceApi.getDeviceLogs(7, { pageNum: 1, pageSize: 20 })

    expect(result).toEqual(payload)
    expect(result.data.records).toHaveLength(1)
    expect(result.data.total).toBe(1)
  })
})
