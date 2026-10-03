import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import api from '../axios'
import { otaApi } from '../ota'

/** 构造一个 axios 成功响应体并交给自定义 adapter 返回。 */
function okResponse(data, status = 200) {
  return { data, status, statusText: 'OK', headers: {}, config: {} }
}

describe('api/ota', () => {
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

  it('listFirmwares 请求 GET /ota/firmwares 并透传 productId 过滤', async () => {
    await otaApi.listFirmwares({ productId: 3 })

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/ota/firmwares')
    expect(config.params).toEqual({ productId: 3 })
  })

  it('listFirmwares 不传参数时查询串为空', async () => {
    await otaApi.listFirmwares()

    const config = adapter.mock.calls[0][0]
    expect(config.url).toBe('/ota/firmwares')
    expect(config.params).toBeUndefined()
  })

  it('firmwareDetail 请求 GET /ota/firmwares/{id}', async () => {
    await otaApi.firmwareDetail(7)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.url).toBe('/ota/firmwares/7')
  })

  it('downloadFirmware 请求下载地址并以 blob 接收', async () => {
    await otaApi.downloadFirmware(7)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.url).toBe('/ota/firmwares/7/download')
    expect(config.responseType).toBe('blob')
  })

  it('uploadFirmware 以 POST /ota/firmwares 提交 multipart 表单', async () => {
    const formData = new FormData()
    formData.append('productId', '3')
    formData.append('version', '1.0.0')
    formData.append('description', '首次发布')
    formData.append('file', new Blob(['firmware']), 'app-1.0.0.bin')

    await otaApi.uploadFirmware(formData)

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('post')
    expect(request.url).toBe('/ota/firmwares')
    expect(request.headers['Content-Type']).toBe('multipart/form-data')
    expect(request.data).toBeInstanceOf(FormData)
    expect(request.data.get('productId')).toBe('3')
    expect(request.data.get('version')).toBe('1.0.0')
    expect(request.data.get('description')).toBe('首次发布')
    expect(request.data.get('file')).toBeInstanceOf(Blob)
  })

  it('removeFirmware 使用 DELETE /ota/firmwares/{id}', async () => {
    await otaApi.removeFirmware(7)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('delete')
    expect(config.url).toBe('/ota/firmwares/7')
  })

  it('createTask 使用 POST /ota/tasks 并提交 JSON Body', async () => {
    const payload = {
      name: '灰度升级',
      firmwareId: 12,
      target: { deviceIds: [1, 2], groupIds: [5], tagIds: [] }
    }

    await otaApi.createTask(payload)

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('post')
    expect(request.url).toBe('/ota/tasks')
    expect(JSON.parse(request.data)).toEqual(payload)
  })

  it('listTasks 请求 GET /ota/tasks 并透传 page / size', async () => {
    await otaApi.listTasks({ page: 1, size: 20 })

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.url).toBe('/ota/tasks')
    expect(config.params).toEqual({ page: 1, size: 20 })
  })

  it('taskDetail 请求 GET /ota/tasks/{id}', async () => {
    await otaApi.taskDetail(9)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.url).toBe('/ota/tasks/9')
  })

  it('taskRecords 请求 GET /ota/tasks/{id}/records 并透传分页与状态过滤', async () => {
    await otaApi.taskRecords(9, { status: 'FAILED', page: 2, size: 50 })

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('get')
    expect(config.url).toBe('/ota/tasks/9/records')
    expect(config.params).toEqual({ status: 'FAILED', page: 2, size: 50 })
  })

  it('retryTask 使用 POST /ota/tasks/{id}/retry', async () => {
    await otaApi.retryTask(9)

    const request = adapter.mock.calls[0][0]
    expect(request.method).toBe('post')
    expect(request.url).toBe('/ota/tasks/9/retry')
  })

  it('removeTask 使用 DELETE /ota/tasks/{id}', async () => {
    await otaApi.removeTask(9)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('delete')
    expect(config.url).toBe('/ota/tasks/9')
  })

  it('返回完整 Result 包装体，交由调用方 unwrap', async () => {
    const payload = {
      code: 200,
      message: 'ok',
      data: { records: [{ id: 9, name: '灰度升级', status: 'RUNNING' }], total: 1 }
    }
    adapter.mockResolvedValue(okResponse(payload))

    const result = await otaApi.listTasks({ page: 1, size: 20 })

    expect(result).toEqual(payload)
    expect(result.data.records[0].status).toBe('RUNNING')
  })
})
